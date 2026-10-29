import { Server } from 'socket.io';
import { logger } from '../utils/logger';
import { getRelatedSongs } from '../services/musicService';
import { activeSessions } from '../websocket/jamHandler';
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

  // Socket tracking
  // socketId -> SocketMetadata
  private socketMeta = new Map<string, SocketMetadata>();
  // code -> (userId -> Set<socketId>)
  private roomUserSockets = new Map<string, Map<string, Set<string>>>();

  // Timers
  // code -> fallback timeout
  private trackFallbackTimers = new Map<string, NodeJS.Timeout>();
  // code -> host handover timeout
  private hostGraceTimers = new Map<string, NodeJS.Timeout>();
  // code:trackSeq inflight recs flag
  private inflightRecs = new Set<string>();

  // Background interval timers
  private pruneInterval: NodeJS.Timeout | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;

  // Rate limiting timestamp windows
  // key -> timestamps[]
  private cmdRateLimits = new Map<string, number[]>();
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
      return false; // rate limited
    }
    timestamps.push(now);
    this.cmdRateLimits.set(key, timestamps);
    return true; // allowed
  }

  checkJoinFailRateLimit(userId: string, max = 5, windowMs = 60000): boolean {
    const now = Date.now();
    const timestamps = (this.joinFailRateLimits.get(userId) || []).filter(
      (ts) => now - ts < windowMs,
    );
    if (timestamps.length >= max) {
      this.joinFailRateLimits.set(userId, timestamps);
      return false; // rate limited
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
      if (!this.store.hasRoom(code) && !activeSessions.has(code)) {
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
    const room = await this.store.getRoom(normalizedCode);
    if (!room) {
      return { error: { code: 'NOT_FOUND', message: 'Room not found' } };
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

    // If the host rejoined, cancel any pending host handover timer
    if (userId === res.room.hostUserId) {
      this.cancelHostGraceTimer(normalizedCode);
    }

    this.store.setRoom(res.room);

    // Broadcast any events (e.g. member_joined) and state update
    if (res.events.length > 0) {
      for (const ev of res.events) {
        this.broadcastEvent(normalizedCode, ev);
      }
    }
    this.broadcastState(res.room);

    return res;
  }

  async leaveRoom(
    code: string,
    userId: string,
  ): Promise<{ success: boolean; error?: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();
    const room = await this.store.getRoom(normalizedCode);
    if (!room) {
      return { success: false, error: { code: 'NOT_FOUND', message: 'Room not found' } };
    }

    if (!room.members.has(userId)) {
      return { success: false, error: { code: 'NOT_MEMBER', message: 'Not a member of this room' } };
    }

    const isHost = room.hostUserId === userId;
    const now = Date.now();

    // If host leaves explicitly, role passes immediately to earliest-joined online member
    if (isHost) {
      this.cancelHostGraceTimer(normalizedCode);

      // Find candidate new host (other members who are online)
      const onlineMembers = Array.from(room.members.values()).filter(
        (m) => m.userId !== userId && m.online,
      );
      onlineMembers.sort((a, b) => a.joinedAt - b.joinedAt);

      if (onlineMembers.length > 0) {
        const nextHost = onlineMembers[0];
        const handoverRes = applyCommand(
          room,
          { type: 'hostHandover', newHostUserId: nextHost.userId },
          'system',
          now,
        );
        if ('room' in handoverRes) {
          Object.assign(room, handoverRes.room);
          for (const ev of handoverRes.events) {
            this.broadcastEvent(normalizedCode, ev);
          }
        }
      }
    }

    // Apply memberLeave
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

    // Check if room is now empty
    if (updatedRoom.members.size === 0) {
      await this.destroyRoom(normalizedCode, 'expired');
      return { success: true };
    }

    this.store.setRoom(updatedRoom);
    for (const ev of leaveRes.events) {
      this.broadcastEvent(normalizedCode, ev);
    }
    this.broadcastState(updatedRoom);

    return { success: true };
  }

  async endRoom(
    code: string,
    hostUserId: string,
  ): Promise<{ success: boolean; error?: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();
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

    await this.destroyRoom(normalizedCode, 'ended');
    return { success: true };
  }

  async executeCommand(
    code: string,
    cmd: ClientCommand,
    actorUserId: string,
  ): Promise<{ room: Room; events: Jam2Event[] } | { error: Jam2Error }> {
    const normalizedCode = code.trim().toUpperCase();
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
    this.store.setRoom(nextRoom);

    // Update track fallback timer if playback changed
    this.updateTrackFallbackTimer(nextRoom);

    // Check recommendations if queue is getting low
    this.triggerRecommendationsIfNeeded(nextRoom);

    // Broadcast state and events
    this.broadcastState(nextRoom);
    for (const ev of res.events) {
      this.broadcastEvent(normalizedCode, ev);
    }

    return res;
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

    // If host reconnected, cancel any grace timer
    this.store.getRoom(normalizedCode).then((room) => {
      if (room && room.hostUserId === userId) {
        this.cancelHostGraceTimer(normalizedCode);
      }
    });
  }

  handleSocketDisconnect(socketId: string): void {
    const meta = this.socketMeta.get(socketId);
    if (!meta) return;

    this.socketMeta.delete(socketId);
    const { code, userId } = meta;

    const roomMap = this.roomUserSockets.get(code);
    if (!roomMap) return;

    const userSockets = roomMap.get(userId);
    if (!userSockets) return;

    userSockets.delete(socketId);
    if (userSockets.size > 0) {
      return; // User still has active sockets in this room
    }

    // User has 0 active sockets in this room -> mark user offline
    roomMap.delete(userId);
    if (roomMap.size === 0) {
      this.roomUserSockets.delete(code);
    }

    runSafely(`handleSocketDisconnect:${code}:${userId}`, async () => {
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

        // If the disconnected user was the host, start the 20s grace period timer
        if (res.room.hostUserId === userId) {
          this.startHostGraceTimer(code, userId);
        }
      }
    });
  }

  // ─── Host Grace Period Timer (20s) ───────────────────────────────────

  private startHostGraceTimer(code: string, oldHostUserId: string): void {
    this.cancelHostGraceTimer(code);

    const timer = setTimeout(() => {
      this.hostGraceTimers.delete(code);
      runSafely(`hostGraceExpiry:${code}`, async () => {
        const room = await this.store.getRoom(code);
        if (!room) return;

        // If host returned and is online, nothing to do
        const currentHost = room.members.get(room.hostUserId);
        if (currentHost && currentHost.online) return;

        // Host is still offline after 20s. Find earliest-joined online member.
        const onlineMembers = Array.from(room.members.values()).filter(
          (m) => m.userId !== oldHostUserId && m.online,
        );
        onlineMembers.sort((a, b) => a.joinedAt - b.joinedAt);

        if (onlineMembers.length > 0) {
          const newHost = onlineMembers[0];
          logger.info(
            { code, oldHostUserId, newHostUserId: newHost.userId },
            'Transferring host after 20s offline grace period',
          );

          const handoverRes = applyCommand(
            room,
            { type: 'hostHandover', newHostUserId: newHost.userId },
            'system',
            Date.now(),
          );

          if ('room' in handoverRes) {
            this.store.setRoom(handoverRes.room);
            this.broadcastState(handoverRes.room);
            for (const ev of handoverRes.events) {
              this.broadcastEvent(code, ev);
            }
          }
        } else {
          logger.info(
            { code, oldHostUserId },
            'Host offline after 20s, but no online members available for handover; room stays offline',
          );
        }
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
    if (durationSec <= 0) return;

    const durationMs = durationSec * 1000;
    const now = Date.now();
    const expectedPos = getExpectedPosition(room.nowPlaying, now);
    const delay = Math.max(500, durationMs - expectedPos + 5000); // 5s grace period

    const timer = setTimeout(() => {
      this.trackFallbackTimers.delete(room.code);
      runSafely(`trackFallbackTimer:${room.code}`, async () => {
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
    }, delay);

    timer.unref();
    this.trackFallbackTimers.set(room.code, timer);
  }

  // ─── Recommendations Side-Effect ────────────────────────────────────

  triggerRecommendationsIfNeeded(room: Room): void {
    if (!room.nowPlaying.item) return;

    // Count upcoming user-added songs
    const userAddedCount = room.queue.filter((q) => !q.recommended).length;
    if (userAddedCount > 1) return;

    const key = `${room.code}:${room.nowPlaying.trackSeq}`;
    if (this.inflightRecs.has(key)) return;

    this.inflightRecs.add(key);

    runSafely(`fetchRecs:${key}`, async () => {
      try {
        const song = room.nowPlaying.item!.song;
        const artist = song.artists?.[0]?.name;
        const candidates = await this.fetchRelatedSongs(
          song.id,
          song.providerId,
          song.title,
          artist,
        );

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
      } catch (err) {
        // Recommendations fetch failures must never break room playback
        logger.warn(
          { err, code: room.code },
          'Failed to fetch recommendations for jam2 room (swallowed)',
        );
      } finally {
        this.inflightRecs.delete(key);
      }
    });
  }

  // ─── Pruning & Expiry Background Task ────────────────────────────────

  private startBackgroundJobs(): void {
    // Check every 30s for offline pruning and room expiry
    this.pruneInterval = setInterval(() => {
      runSafely('jam2:pruneInterval', async () => {
        const rooms = this.store.getAllActiveRooms();
        const now = Date.now();

        for (const room of rooms) {
          // 1. Prune non-host members offline >10 minutes (600,000 ms)
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

          // 2. Room expiry check
          const allOffline = Array.from(room.members.values()).every(
            (m) => !m.online,
          );
          if (allOffline) {
            const longestInactive = Math.max(
              ...Array.from(room.members.values()).map((m) => m.lastSeenAt),
              room.createdAt,
            );
            // Expire if all members have been offline >30 minutes (1,800,000 ms) or if 0 members
            if (
              room.members.size === 0 ||
              now - longestInactive > 30 * 60 * 1000
            ) {
              logger.info({ code: room.code }, 'Jam2 room expired due to inactivity');
              await this.destroyRoom(room.code, 'expired');
            }
          }
        }
      });
    }, 30000);
    this.pruneInterval.unref();

    // Heartbeat every 10s: broadcast full state to rooms with online members
    this.heartbeatInterval = setInterval(() => {
      runSafely('jam2:heartbeatInterval', () => {
        const rooms = this.store.getAllActiveRooms();
        for (const room of rooms) {
          const hasOnline = Array.from(room.members.values()).some(
            (m) => m.online,
          );
          if (hasOnline) {
            this.broadcastState(room);
          }
        }
      });
    }, 10000);
    this.heartbeatInterval.unref();
  }

  async destroyRoom(code: string, reason: 'ended' | 'expired'): Promise<void> {
    const normalizedCode = code.toUpperCase();
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
    await this.store.deleteRoom(normalizedCode);
  }

  // ─── Socket.IO Broadcast Helpers ─────────────────────────────────────

  broadcastState(room: Room): void {
    if (!this.io) return;
    const snapshot: RoomSnapshot = serializeRoom(room, Date.now());
    this.io.to(`jam2:${room.code}`).emit('jam2:state', snapshot);
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
