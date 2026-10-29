import { Server } from 'socket.io';
import { logger } from '../utils/logger';
import { getRelatedSongs } from '../services/musicService';
import { jam2Store, Jam2Store } from './store';
import { applyCommand } from './reducer';
import {
  Room,
  RoomSnapshot,
  serializeRoom,
  getExpectedPosition,
  ClientCommand,
  Jam2Event,
  Jam2Error,
  JAM_ROOM_CODE_CHARS,
  DEFAULT_ROOM_PERMS,
  Song,
  Jam2Tick,
} from './types';

export function runSafely(label: string, fn: () => void | Promise<void>): void {
  try {
    const res = fn();
    if (res && typeof (res as Promise<void>).catch === 'function') {
      (res as Promise<void>).catch((err) => {
        logger.error({ err, label }, 'Unhandled async error in Jam2 task');
      });
    }
  } catch (err) {
    logger.error({ err, label }, 'Unhandled sync error in Jam2 task');
  }
}

export class KeyedMutex {
  private chains = new Map<string, Promise<unknown>>();

  async runExclusive<T>(key: string, fn: () => Promise<T>): Promise<T> {
    const normalizedKey = key.trim().toUpperCase();
    const prev = this.chains.get(normalizedKey) ?? Promise.resolve();

    let release: () => void;
    const current = new Promise<void>((resolve) => {
      release = resolve;
    });

    const chainPromise = prev.catch(() => {}).then(() => current);
    this.chains.set(normalizedKey, chainPromise);

    try {
      await prev.catch(() => {});
      return await fn();
    } finally {
      release!();
      if (this.chains.get(normalizedKey) === chainPromise) {
        this.chains.delete(normalizedKey);
      }
    }
  }
}

export type RelatedSongsFetcher = (
  songId: string,
  providerId?: string,
  seedTitle?: string,
  seedArtist?: string,
) => Promise<Song[]>;

interface SocketMetadata {
  code: string;
  userId: string;
  username: string;
}

export class Jam2Manager {
  private io: Server | null = null;
  private store: Jam2Store;
  private fetchRelatedSongs: RelatedSongsFetcher;
  private mutex = new KeyedMutex();

  // Socket tracking
  private socketMeta = new Map<string, SocketMetadata>();
  private roomUserSockets = new Map<string, Map<string, Set<string>>>();

  // Timers
  private trackFallbackTimers = new Map<string, NodeJS.Timeout>();
  private hostGraceTimers = new Map<string, NodeJS.Timeout>();
  private inflightRecs = new Set<string>();
  private recsSeededForTrackSeq = new Set<string>();
  private kickedUsers = new Map<string, Map<string, number>>();
  private loggedZeroDuration = new Set<string>();

  // Background interval timers
  private pruneInterval: NodeJS.Timeout | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;

  // Rate limiting timestamp windows
  private cmdRateLimits = new Map<string, number[]>();
  private createRateLimits = new Map<string, number[]>();
  private joinFailRateLimits = new Map<string, number[]>();
  private timeRateLimits = new Map<string, number[]>();

  constructor(
    store: Jam2Store = jam2Store,
    fetcher: RelatedSongsFetcher = getRelatedSongs,
  ) {
    this.store = store;
    this.fetchRelatedSongs = fetcher;
    this.startBackgroundJobs();
  }

  setIO(io: Server): void {
    this.io = io;
  }

  // ─── Rate Limiting ───────────────────────────────────────────────────

  checkCmdRateLimit(key: string, max = 20, windowMs = 10000): boolean {
    const now = Date.now();
    const timestamps = (this.cmdRateLimits.get(key) || []).filter(
      (ts) => now - ts < windowMs,
    );
    if (timestamps.length >= max) {
      this.cmdRateLimits.set(key, timestamps);
      return false;
    }
    timestamps.push(now);
    this.cmdRateLimits.set(key, timestamps);
    return true;
  }

  checkCreateRateLimit(userId: string, max = 5, windowMs = 60000): boolean {
    const now = Date.now();
    const timestamps = (this.createRateLimits.get(userId) || []).filter(
      (ts) => now - ts < windowMs,
    );
    if (timestamps.length >= max) {
      this.createRateLimits.set(userId, timestamps);
      return false;
    }
    timestamps.push(now);
    this.createRateLimits.set(userId, timestamps);
    return true;
  }

  clearCreateRateLimits(): void {
    this.createRateLimits.clear();
  }

  getActiveRoomForUser(userId: string): string | null {
    for (const [code, roomMap] of this.roomUserSockets.entries()) {
      if (roomMap.has(userId) && (roomMap.get(userId)?.size ?? 0) > 0) {
        return code;
      }
    }
    return null;
  }

  getUserSockets(code: string, userId: string): string[] {
    const normalizedCode = code.toUpperCase();
    const set = this.roomUserSockets.get(normalizedCode)?.get(userId);
    return set ? Array.from(set) : [];
  }

  checkJoinFailRateLimit(userId: string, max = 5, windowMs = 60000): boolean {
    const now = Date.now();
    const timestamps = (this.joinFailRateLimits.get(userId) || []).filter(
      (ts) => now - ts < windowMs,
    );
    if (timestamps.length >= max) {
      this.joinFailRateLimits.set(userId, timestamps);
      return false;
    }
    return true;
  }

  recordJoinFailure(userId: string, windowMs = 60000): void {
    const now = Date.now();
    const timestamps = (this.joinFailRateLimits.get(userId) || []).filter(
      (ts) => now - ts < windowMs,
    );
    timestamps.push(now);
    this.joinFailRateLimits.set(userId, timestamps);
  }

  checkTimeRateLimit(key: string, max = 30, windowMs = 60000): boolean {
    const now = Date.now();
    const timestamps = (this.timeRateLimits.get(key) || []).filter(
      (ts) => now - ts < windowMs,
    );
    if (timestamps.length >= max) {
      this.timeRateLimits.set(key, timestamps);
      return false;
    }
    timestamps.push(now);
    this.timeRateLimits.set(key, timestamps);
    return true;
  }

  // ─── Room Code Generation ────────────────────────────────────────────

  generateRoomCode(): string {
    for (let attempt = 0; attempt < 100; attempt++) {
      let code = '';
      for (let i = 0; i < 4; i++) {
        code += JAM_ROOM_CODE_CHARS[
          Math.floor(Math.random() * JAM_ROOM_CODE_CHARS.length)
        ];
      }
      if (!this.store.hasRoom(code)) {
        return code;
      }
    }
    return `J${Date.now().toString(36).slice(-3).toUpperCase()}`;
  }

  // ─── Room Lifecycle ──────────────────────────────────────────────────

  async createRoom(
    hostUserId: string,
    hostUsername: string,
    avatarUrl?: string,
  ): Promise<Room> {
    const code = this.generateRoomCode();
    const now = Date.now();

    return this.mutex.runExclusive(code, async () => {
      const room: Room = {
        code,
        version: 1,
        hostUserId,
        createdAt: now,
        members: new Map([
          [
            hostUserId,
            {
              userId: hostUserId,
              username: hostUsername,
              avatarUrl,
              joinedAt: now,
              online: true,
              lastSeenAt: now,
              perms: {
                queueAdd: true,
                playback: true,
                queueManage: true,
              },
            },
          ],
        ]),
        defaults: { ...DEFAULT_ROOM_PERMS },
        queue: [],
        history: [],
        nowPlaying: {
          item: null,
          status: 'idle',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: 0,
        },
      };

      this.store.setRoom(room);
      logger.info({ code, hostUserId, hostUsername }, 'Jam2 room created');
      return room;
    });
  }

  async getRoom(code: string): Promise<Room | null> {
    return this.store.getRoom(code.toUpperCase());
  }

  async joinRoom(
    code: string,
    userId: string,
    username: string,
    avatarUrl?: string,
  ): Promise<{ room: Room; events: Jam2Event[] } | { error: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();

    // Check kick ban (10 minutes)
    const roomKicks = this.kickedUsers.get(normalizedCode);
    if (roomKicks) {
      const expiry = roomKicks.get(userId);
      if (expiry && expiry > Date.now()) {
        return {
          error: {
            code: 'FORBIDDEN',
            message: 'You were removed from this Jam by the host.',
          },
        };
      }
    }

    return this.mutex.runExclusive(normalizedCode, async () => {
      const room = await this.store.getRoom(normalizedCode);
      if (!room) {
        return { error: { code: 'NOT_FOUND', message: 'Room not found' } };
      }

      // Resume join for already-online member: do NOT bump version or broadcast
      const existingMember = room.members.get(userId);
      if (existingMember && existingMember.online) {
        return { room, events: [] };
      }

      const now = Date.now();
      const res = applyCommand(
        room,
        {
          type: 'memberOnline',
          userId,
          username,
          avatarUrl,
        },
        'system',
        now,
      );

      if ('error' in res) {
        return res;
      }

      // If host rejoined, cancel any pending host handover timer
      if (userId === res.room.hostUserId) {
        this.cancelHostGraceTimer(normalizedCode);
      }

      // Call ensureHost right after every successful join
      this.ensureHostLocked(res.room, now);

      this.store.setRoom(res.room);

      // Arm track-fallback timer if playing and not armed
      if (res.room.nowPlaying.status === 'playing' && res.room.nowPlaying.item) {
        if (!this.trackFallbackTimers.has(normalizedCode)) {
          this.updateTrackFallbackTimer(res.room);
        }
      }

      // Broadcast events and state
      if (res.events.length > 0) {
        for (const ev of res.events) {
          this.broadcastEvent(normalizedCode, ev);
        }
      }
      this.broadcastState(res.room);

      return res;
    });
  }

  async leaveRoom(
    code: string,
    userId: string,
  ): Promise<{ success: boolean; error?: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();

    return this.mutex.runExclusive(normalizedCode, async () => {
      const room = await this.store.getRoom(normalizedCode);
      if (!room) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Room not found' } };
      }

      if (!room.members.has(userId)) {
        return { success: false, error: { code: 'NOT_MEMBER', message: 'Not a member of this room' } };
      }

      const isHost = room.hostUserId === userId;
      const now = Date.now();

      if (isHost) {
        this.cancelHostGraceTimer(normalizedCode);
      }

      // Apply memberLeave (reducer picks successor preferring online, falling back to offline)
      const leaveRes = applyCommand(
        room,
        { type: 'memberLeave', userId },
        'system',
        now,
      );

      if ('error' in leaveRes) {
        return { success: false, error: leaveRes.error };
      }

      const updatedRoom = leaveRes.room;

      if (updatedRoom.members.size === 0) {
        await this.destroyRoomLocked(normalizedCode, 'expired');
        return { success: true };
      }

      this.store.setRoom(updatedRoom);
      for (const ev of leaveRes.events) {
        this.broadcastEvent(normalizedCode, ev);
      }
      this.broadcastState(updatedRoom);

      return { success: true };
    });
  }

  async endRoom(
    code: string,
    hostUserId: string,
  ): Promise<{ success: boolean; error?: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();

    return this.mutex.runExclusive(normalizedCode, async () => {
      const room = await this.store.getRoom(normalizedCode);
      if (!room) {
        return { success: false, error: { code: 'NOT_FOUND', message: 'Room not found' } };
      }

      if (room.hostUserId !== hostUserId) {
        return {
          success: false,
          error: { code: 'FORBIDDEN', message: 'Only the host can end the Jam session' },
        };
      }

      await this.destroyRoomLocked(normalizedCode, 'ended');
      return { success: true };
    });
  }

  async executeCommand(
    code: string,
    cmd: ClientCommand,
    actorUserId: string,
  ): Promise<{
    room: Room;
    events: Jam2Event[];
    added?: number;
    skipped?: number;
    skippedDuplicate?: number;
    skippedFull?: number;
  } | { error: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();

    return this.mutex.runExclusive(normalizedCode, async () => {
      const room = await this.store.getRoom(normalizedCode);
      if (!room) {
        return { error: { code: 'NOT_FOUND', message: 'Room not found' } };
      }

      const now = Date.now();
      const res = applyCommand(room, cmd, actorUserId, now);
      if ('error' in res) {
        return res;
      }

      const nextRoom = res.room;

      // Skip persistence and broadcast if state is unchanged (no-op)
      if (nextRoom.version === room.version) {
        return {
          room: nextRoom,
          events: res.events,
          added: res.added,
          skipped: (res.skippedDuplicate ?? 0) + (res.skippedFull ?? 0),
          skippedDuplicate: res.skippedDuplicate,
          skippedFull: res.skippedFull,
        };
      }

      // Handle kick tracking for 10 minutes
      if (cmd.type === 'kick') {
        let kicks = this.kickedUsers.get(normalizedCode);
        if (!kicks) {
          kicks = new Map<string, number>();
          this.kickedUsers.set(normalizedCode, kicks);
        }
        kicks.set(cmd.userId, Date.now() + 10 * 60 * 1000);
      }

      this.store.setRoom(nextRoom);

      // Update track fallback timer if playback changed
      this.updateTrackFallbackTimer(nextRoom);

      // Check recommendations
      this.triggerRecommendationsIfNeeded(nextRoom);

      // Broadcast state and events
      this.broadcastState(nextRoom);
      for (const ev of res.events) {
        this.broadcastEvent(normalizedCode, ev);
      }

      return {
        room: nextRoom,
        events: res.events,
        added: res.added,
        skipped: (res.skippedDuplicate ?? 0) + (res.skippedFull ?? 0),
        skippedDuplicate: res.skippedDuplicate,
        skippedFull: res.skippedFull,
      };
    });
  }

  // ─── Socket Connection & Disconnect Handling ─────────────────────────

  registerSocket(socketId: string, code: string, userId: string, username: string): void {
    const normalizedCode = code.toUpperCase();
    this.socketMeta.set(socketId, { code: normalizedCode, userId, username });

    let roomMap = this.roomUserSockets.get(normalizedCode);
    if (!roomMap) {
      roomMap = new Map<string, Set<string>>();
      this.roomUserSockets.set(normalizedCode, roomMap);
    }

    let userSockets = roomMap.get(userId);
    if (!userSockets) {
      userSockets = new Set<string>();
      roomMap.set(userId, userSockets);
    }
    userSockets.add(socketId);

    this.store.getRoom(normalizedCode).then((room) => {
      if (room && room.hostUserId === userId) {
        this.cancelHostGraceTimer(normalizedCode);
      }
    });
  }

  deregisterUserSockets(code: string, userId: string): void {
    const normalizedCode = code.toUpperCase();
    const roomMap = this.roomUserSockets.get(normalizedCode);
    if (roomMap) {
      const userSockets = roomMap.get(userId);
      if (userSockets) {
        for (const socketId of userSockets) {
          const meta = this.socketMeta.get(socketId);
          if (meta && meta.code === normalizedCode) {
            this.socketMeta.delete(socketId);
          }
        }
        roomMap.delete(userId);
      }
      if (roomMap.size === 0) {
        this.roomUserSockets.delete(normalizedCode);
      }
    }
  }

  handleSocketDisconnect(socketId: string): void {
    const meta = this.socketMeta.get(socketId);
    if (!meta) return;

    this.socketMeta.delete(socketId);
    this.cmdRateLimits.delete(socketId);
    this.timeRateLimits.delete(socketId);

    const { code, userId } = meta;

    const roomMap = this.roomUserSockets.get(code);
    if (!roomMap) return;

    const userSockets = roomMap.get(userId);
    if (!userSockets) return;

    userSockets.delete(socketId);
    if (userSockets.size > 0) {
      return;
    }

    roomMap.delete(userId);
    if (roomMap.size === 0) {
      this.roomUserSockets.delete(code);
    }

    runSafely(`handleSocketDisconnect:${code}:${userId}`, async () => {
      await this.mutex.runExclusive(code, async () => {
        const room = await this.store.getRoom(code);
        if (!room) return;

        const now = Date.now();
        const res = applyCommand(
          room,
          { type: 'memberOffline', userId },
          'system',
          now,
        );

        if ('room' in res) {
          this.store.setRoom(res.room);
          this.broadcastState(res.room);

          if (res.room.hostUserId === userId) {
            this.startHostGraceTimer(code, userId);
          }
        }
      });
    });
  }

  // ─── Host Grace Period Timer (20s) ───────────────────────────────────

  private startHostGraceTimer(code: string, _oldHostUserId: string): void {
    this.cancelHostGraceTimer(code);

    const timer = setTimeout(() => {
      this.hostGraceTimers.delete(code);
      runSafely(`hostGraceExpiry:${code}`, async () => {
        await this.ensureHost(code);
      });
    }, 20000);

    timer.unref();
    this.hostGraceTimers.set(code, timer);
  }

  private cancelHostGraceTimer(code: string): void {
    const existing = this.hostGraceTimers.get(code);
    if (existing) {
      clearTimeout(existing);
      this.hostGraceTimers.delete(code);
    }
  }

  async ensureHost(code: string, now = Date.now()): Promise<void> {
    const normalizedCode = code.trim().toUpperCase();
    await this.mutex.runExclusive(normalizedCode, async () => {
      const room = await this.store.getRoom(normalizedCode, now);
      if (!room) return;
      this.ensureHostLocked(room, now);
    });
  }

  private ensureHostLocked(room: Room, now: number): void {
    const currentHost = room.members.get(room.hostUserId);
    if (!currentHost || (!currentHost.online && now - currentHost.lastSeenAt >= 20000)) {
      const onlineMembers = Array.from(room.members.values()).filter((m) => m.online);
      onlineMembers.sort((a, b) => a.joinedAt - b.joinedAt);

      if (onlineMembers.length > 0) {
        const nextHost = onlineMembers[0];
        if (room.hostUserId !== nextHost.userId) {
          logger.info(
            { code: room.code, oldHostUserId: room.hostUserId, newHostUserId: nextHost.userId },
            'Transferring host via ensureHost',
          );
          const handoverRes = applyCommand(
            room,
            { type: 'hostHandover', newHostUserId: nextHost.userId },
            'system',
            now,
          );
          if ('room' in handoverRes) {
            Object.assign(room, handoverRes.room);
            this.store.setRoom(room);
            this.broadcastState(room);
            for (const ev of handoverRes.events) {
              this.broadcastEvent(room.code, ev);
            }
          }
        }
      }
    }
  }

  // ─── Track Fallback Timer ────────────────────────────────────────────

  private updateTrackFallbackTimer(room: Room): void {
    const existing = this.trackFallbackTimers.get(room.code);
    if (existing) {
      clearTimeout(existing);
      this.trackFallbackTimers.delete(room.code);
    }

    if (room.nowPlaying.status !== 'playing' || !room.nowPlaying.item) {
      return;
    }

    const durationSec = room.nowPlaying.item.song.duration;
    if (durationSec <= 0) {
      if (!this.loggedZeroDuration.has(room.code)) {
        this.loggedZeroDuration.add(room.code);
        logger.warn({ code: room.code }, 'Cannot set fallback timer for track with unknown duration (0)');
      }
      return;
    }

    const durationMs = durationSec * 1000;
    const now = Date.now();
    const expectedPos = getExpectedPosition(room.nowPlaying, now);
    const delay = Math.max(500, durationMs - expectedPos + 5000);

    const timer = setTimeout(() => {
      this.trackFallbackTimers.delete(room.code);
      runSafely(`trackFallbackTimer:${room.code}`, async () => {
        await this.mutex.runExclusive(room.code, async () => {
          const freshRoom = await this.store.getRoom(room.code);
          if (
            !freshRoom ||
            freshRoom.nowPlaying.status !== 'playing' ||
            !freshRoom.nowPlaying.item ||
            freshRoom.nowPlaying.trackSeq !== room.nowPlaying.trackSeq
          ) {
            return;
          }

          logger.debug(
            { code: room.code, trackSeq: room.nowPlaying.trackSeq },
            'Track fallback timer triggered trackEnded',
          );

          const res = applyCommand(
            freshRoom,
            {
              code: room.code,
              type: 'trackEnded',
              trackSeq: freshRoom.nowPlaying.trackSeq,
            },
            'system',
            Date.now(),
          );

          if ('room' in res) {
            this.store.setRoom(res.room);
            this.updateTrackFallbackTimer(res.room);
            this.triggerRecommendationsIfNeeded(res.room);
            this.broadcastState(res.room);
            for (const ev of res.events) {
              this.broadcastEvent(room.code, ev);
            }
          }
        });
      });
    }, delay);

    timer.unref();
    this.trackFallbackTimers.set(room.code, timer);
  }

  // ─── Recommendations Side-Effect ────────────────────────────────────

  triggerRecommendationsIfNeeded(room: Room): void {
    if (!room.nowPlaying.item) return;

    const userAddedCount = room.queue.filter((q) => !q.recommended).length;
    const recCount = room.queue.filter((q) => q.recommended).length;

    // A7: Only fetch when recommended items in queue < 3 AND user items <= 1
    if (userAddedCount > 1 || recCount >= 3) return;

    const seedKey = `${room.code}:${room.nowPlaying.trackSeq}`;
    if (this.recsSeededForTrackSeq.has(seedKey)) return;
    this.recsSeededForTrackSeq.add(seedKey);

    const inflightKey = `${room.code}:${room.nowPlaying.trackSeq}`;
    if (this.inflightRecs.has(inflightKey)) return;
    this.inflightRecs.add(inflightKey);

    runSafely(`fetchRecs:${inflightKey}`, async () => {
      try {
        const song = room.nowPlaying.item!.song;
        const artist = song.artists?.[0]?.name;

        // Fetch OUTSIDE mutex lock
        const candidates = await this.fetchRelatedSongs(
          song.id,
          song.providerId,
          song.title,
          artist,
        );

        // Apply INSIDE mutex lock
        await this.mutex.runExclusive(room.code, async () => {
          const freshRoom = await this.store.getRoom(room.code);
          if (
            !freshRoom ||
            freshRoom.nowPlaying.trackSeq !== room.nowPlaying.trackSeq
          ) {
            return;
          }

          const res = applyCommand(
            freshRoom,
            {
              type: 'recsAdded',
              forTrackSeq: room.nowPlaying.trackSeq,
              songs: candidates,
            },
            'system',
            Date.now(),
          );

          if ('room' in res && res.room.version !== freshRoom.version) {
            this.store.setRoom(res.room);
            this.broadcastState(res.room);
          }
        });
      } catch (err) {
        logger.warn(
          { err, code: room.code },
          'Failed to fetch recommendations for jam2 room (swallowed)',
        );
      } finally {
        this.inflightRecs.delete(inflightKey);
      }
    });
  }

  // ─── Pruning & Expiry Background Task ────────────────────────────────

  async pruneActiveRooms(now: number = Date.now()): Promise<void> {
    const rooms = this.store.getAllActiveRooms();

    for (const r of rooms) {
      await this.mutex.runExclusive(r.code, async () => {
        // Re-fetch room inside lock to ensure freshness and skip if room destroyed
        const room = await this.store.getRoom(r.code);
        if (!room) return;

        // Prune non-host members offline >10 minutes
        const membersToPrune = Array.from(room.members.values()).filter(
          (m) =>
            m.userId !== room.hostUserId &&
            !m.online &&
            now - m.lastSeenAt > 10 * 60 * 1000,
        );

        for (const m of membersToPrune) {
          const res = applyCommand(
            room,
            { type: 'memberLeave', userId: m.userId },
            'system',
            now,
          );
          if ('room' in res) {
            Object.assign(room, res.room);
            this.store.setRoom(room);
            this.broadcastState(room);
            for (const ev of res.events) {
              this.broadcastEvent(room.code, ev);
            }
          }
        }

        // Ensure host handover if host is offline > 20s
        this.ensureHostLocked(room, now);

        // Check room expiry (>30 minutes all offline or 0 members)
        const allOffline = Array.from(room.members.values()).every(
          (m) => !m.online,
        );
        if (allOffline) {
          const longestInactive = Math.max(
            ...Array.from(room.members.values()).map((m) => m.lastSeenAt),
            room.createdAt,
          );
          if (
            room.members.size === 0 ||
            now - longestInactive > 30 * 60 * 1000
          ) {
            logger.info({ code: room.code }, 'Jam2 room expired due to inactivity');
            await this.destroyRoomLocked(room.code, 'expired');
          }
        }
      });
    }

    // Periodic sweep of rate-limits and kicks
    for (const [key, timestamps] of this.cmdRateLimits.entries()) {
      const fresh = timestamps.filter((ts) => now - ts < 10000);
      if (fresh.length === 0) this.cmdRateLimits.delete(key);
      else this.cmdRateLimits.set(key, fresh);
    }
    for (const [key, timestamps] of this.timeRateLimits.entries()) {
      const fresh = timestamps.filter((ts) => now - ts < 60000);
      if (fresh.length === 0) this.timeRateLimits.delete(key);
      else this.timeRateLimits.set(key, fresh);
    }
    for (const [code, kicks] of this.kickedUsers.entries()) {
      for (const [userId, expiry] of kicks.entries()) {
        if (now >= expiry) kicks.delete(userId);
      }
      if (kicks.size === 0) this.kickedUsers.delete(code);
    }
  }

  private startBackgroundJobs(): void {
    // 30s prune interval
    this.pruneInterval = setInterval(() => {
      runSafely('jam2:pruneInterval', () => this.pruneActiveRooms());
    }, 30000);
    this.pruneInterval.unref();

    // Heartbeat every 10s: broadcast lighter jam2:tick to rooms with online members
    this.heartbeatInterval = setInterval(() => {
      runSafely('jam2:heartbeatInterval', () => {
        const rooms = this.store.getAllActiveRooms();
        for (const room of rooms) {
          const hasOnline = Array.from(room.members.values()).some(
            (m) => m.online,
          );
          if (hasOnline) {
            this.broadcastTick(room);
          }
        }
      });
    }, 10000);
    this.heartbeatInterval.unref();
  }

  async destroyRoom(code: string, reason: 'ended' | 'expired'): Promise<void> {
    const normalizedCode = code.toUpperCase();
    await this.mutex.runExclusive(normalizedCode, async () => {
      await this.destroyRoomLocked(normalizedCode, reason);
    });
  }

  private async destroyRoomLocked(normalizedCode: string, reason: 'ended' | 'expired'): Promise<void> {
    this.cancelHostGraceTimer(normalizedCode);

    const fallbackTimer = this.trackFallbackTimers.get(normalizedCode);
    if (fallbackTimer) {
      clearTimeout(fallbackTimer);
      this.trackFallbackTimers.delete(normalizedCode);
    }

    if (this.io) {
      this.io.to(`jam2:${normalizedCode}`).emit('jam2:closed', { reason });
    }

    this.roomUserSockets.delete(normalizedCode);
    this.recsSeededForTrackSeq.delete(normalizedCode);
    this.kickedUsers.delete(normalizedCode);
    this.loggedZeroDuration.delete(normalizedCode);
    await this.store.deleteRoom(normalizedCode);
  }

  // ─── Socket.IO Broadcast Helpers ─────────────────────────────────────

  broadcastState(room: Room): void {
    if (!this.io) return;
    const snapshot: RoomSnapshot = serializeRoom(room, Date.now());
    this.io.to(`jam2:${room.code}`).emit('jam2:state', snapshot);
  }

  broadcastTick(room: Room): void {
    if (!this.io) return;
    const tick: Jam2Tick = {
      code: room.code,
      version: room.version,
      serverNow: Date.now(),
      nowPlaying: {
        status: room.nowPlaying.status,
        positionMs: room.nowPlaying.positionMs,
        anchorServerTs: room.nowPlaying.anchorServerTs,
        trackSeq: room.nowPlaying.trackSeq,
        qid: room.nowPlaying.item?.qid ?? null,
      },
    };
    this.io.to(`jam2:${room.code}`).emit('jam2:tick', tick);
  }

  broadcastEvent(code: string, event: Jam2Event): void {
    if (!this.io) return;
    this.io.to(`jam2:${code}`).emit('jam2:event', event);
  }

  notifyServerRestart(): void {
    if (!this.io) return;
    for (const room of this.store.getAllActiveRooms()) {
      this.broadcastEvent(room.code, { kind: 'server_restarting' });
    }
  }

  // ─── Lifecycle / Teardown (Tests & Shutdown) ─────────────────────────

  stop(): void {
    if (this.pruneInterval) {
      clearInterval(this.pruneInterval);
      this.pruneInterval = null;
    }
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    for (const timer of this.hostGraceTimers.values()) {
      clearTimeout(timer);
    }
    this.hostGraceTimers.clear();

    for (const timer of this.trackFallbackTimers.values()) {
      clearTimeout(timer);
    }
    this.trackFallbackTimers.clear();
  }
}

export const jam2Manager = new Jam2Manager();
