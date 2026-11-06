import { logger } from '../utils/logger';
import { getRedisClient } from '../config/redis';
import { Room, Member, QueueItem, NowPlaying } from './types';
import { applyCommand } from './reducer';

const REDIS_KEY_PREFIX = 'jam2:room:';
const ROOM_TTL_SECONDS = 12 * 60 * 60; // 12 hours
const WRITE_DEBOUNCE_MS = 1500;

interface PersistedRoom {
  code: string;
  version: number;
  hostUserId: string;
  createdAt: number;
  members: Member[];
  defaults: Record<string, boolean>;
  queue: QueueItem[];
  history: QueueItem[];
  nowPlaying: NowPlaying;
}

export class Jam2Store {
  private rooms = new Map<string, Room>();
  private dirtyRooms = new Set<string>();
  private debounceTimers = new Map<string, NodeJS.Timeout>();
  private inFlightRehydrations = new Map<string, Promise<Room | null>>();
  private tombstones = new Map<string, number>();

  private isTombstoned(code: string, now = Date.now()): boolean {
    const expiry = this.tombstones.get(code);
    if (!expiry) return false;
    if (now >= expiry) {
      this.tombstones.delete(code);
      return false;
    }
    return true;
  }

  /**
   * Return room from memory, or rehydrate from Redis if available (single-flight).
   */
  async getRoom(code: string, now = Date.now()): Promise<Room | null> {
    const normalizedCode = code.trim().toUpperCase();

    // 1. Memory check
    const inMemory = this.rooms.get(normalizedCode);
    if (inMemory) {
      return inMemory;
    }

    // 2. Single-flight in-flight check
    const inFlight = this.inFlightRehydrations.get(normalizedCode);
    if (inFlight) {
      return inFlight;
    }

    // 3. Rehydrate from Redis via separate async method
    const promise = this.rehydrateFromRedis(normalizedCode, now).finally(() => {
      this.inFlightRehydrations.delete(normalizedCode);
    });

    this.inFlightRehydrations.set(normalizedCode, promise);
    return promise;
  }

  private async rehydrateFromRedis(normalizedCode: string, now: number): Promise<Room | null> {
    if (this.isTombstoned(normalizedCode, now)) {
      return null;
    }
    try {
      const client = getRedisClient();
      const raw = await client.get(`${REDIS_KEY_PREFIX}${normalizedCode}`);
      if (this.isTombstoned(normalizedCode)) {
        return null;
      }
      if (!raw) return null;

      const parsed: PersistedRoom = JSON.parse(raw);
      const membersMap = new Map<string, Member>();
      for (const m of parsed.members) {
        membersMap.set(m.userId, {
          ...m,
          online: false, // Mark all offline on rehydration until they rejoin
          lastSeenAt: now, // Give host fresh 20s grace period on restart
        });
      }

      const rehydrated: Room = {
        code: parsed.code,
        version: parsed.version + 1000, // Version + 1000 on rehydrate
        hostUserId: parsed.hostUserId,
        createdAt: parsed.createdAt,
        members: membersMap,
        defaults: parsed.defaults,
        queue: parsed.queue,
        history: parsed.history,
        nowPlaying: parsed.nowPlaying,
      };

      // Catch-up stale playback if song finished while server was offline (Task B10)
      const caughtUp = this.catchUpStalePlayback(rehydrated, now);

      this.rooms.set(normalizedCode, caughtUp);
      logger.info({ code: normalizedCode }, 'Rehydrated jam2 room from Redis');
      return caughtUp;
    } catch (err) {
      logger.warn({ err, code: normalizedCode }, 'Failed to rehydrate jam2 room from Redis (failing open)');
      return null;
    }
  }

  /**
   * Check if a room exists in memory or in Redis.
   */
  async checkRoomExists(code: string): Promise<boolean> {
    const normalizedCode = code.toUpperCase();
    if (this.rooms.has(normalizedCode)) return true;
    if (this.isTombstoned(normalizedCode)) return false;
    try {
      const client = getRedisClient();
      const exists = await client.exists(`${REDIS_KEY_PREFIX}${normalizedCode}`);
      return exists > 0;
    } catch {
      return false;
    }
  }

  /**
   * Refresh Redis TTL for an active room.
   */
  async refreshRoomTTL(code: string): Promise<void> {
    const normalizedCode = code.toUpperCase();
    try {
      const client = getRedisClient();
      await client.expire(`${REDIS_KEY_PREFIX}${normalizedCode}`, ROOM_TTL_SECONDS);
    } catch (err) {
      logger.warn({ err, code: normalizedCode }, 'Failed to refresh jam2 room TTL in Redis');
    }
  }

  /**
   * Set or update a room in memory and schedule debounced Redis write.
   */
  setRoom(room: Room): void {
    const code = room.code.toUpperCase();
    this.tombstones.delete(code);
    this.rooms.set(code, room);
    this.scheduleDebouncedWrite(code);
  }

  /**
   * Check if a room exists in memory.
   */
  hasRoom(code: string): boolean {
    return this.rooms.has(code.toUpperCase());
  }

  /**
   * Check if a room has pending writes (dirty flag).
   */
  isRoomDirty(code: string): boolean {
    return this.dirtyRooms.has(code.toUpperCase());
  }

  /**
   * Delete room from memory and Redis.
   */
  async deleteRoom(code: string): Promise<void> {
    const normalizedCode = code.toUpperCase();
    this.tombstones.set(normalizedCode, Date.now() + 60_000);
    this.rooms.delete(normalizedCode);
    this.dirtyRooms.delete(normalizedCode);

    const pending = this.debounceTimers.get(normalizedCode);
    if (pending) {
      clearTimeout(pending);
      this.debounceTimers.delete(normalizedCode);
    }

    try {
      const client = getRedisClient();
      await client.del(`${REDIS_KEY_PREFIX}${normalizedCode}`);
    } catch (err) {
      logger.warn({ err, code: normalizedCode }, 'Failed to delete jam2 room from Redis (failing open)');
    }
  }

  /**
   * Get all active rooms currently in memory.
   */
  getAllActiveRooms(): Room[] {
    return Array.from(this.rooms.values());
  }

  /**
   * Schedule debounced write to Redis.
   */
  private scheduleDebouncedWrite(code: string): void {
    this.dirtyRooms.add(code);
    if (this.debounceTimers.has(code)) {
      return; // Timer already ticking
    }

    const timer = setTimeout(() => {
      this.debounceTimers.delete(code);
      this.persistRoomToRedis(code).catch((err) => {
        logger.warn({ err, code }, 'Background Redis persist failed');
      });
    }, WRITE_DEBOUNCE_MS);
    timer.unref();
    this.debounceTimers.set(code, timer);
  }

  private scheduleRetryWrite(code: string, attempt: number): void {
    const existing = this.debounceTimers.get(code);
    if (existing) {
      clearTimeout(existing);
      this.debounceTimers.delete(code);
    }
    const delay = Math.min(5000, 500 * Math.pow(2, attempt));
    const timer = setTimeout(() => {
      this.debounceTimers.delete(code);
      this.persistRoomToRedis(code, attempt + 1).catch((err) => {
        logger.warn({ err, code }, 'Retry Redis persist failed');
      });
    }, delay);
    timer.unref();
    this.debounceTimers.set(code, timer);
  }

  /**
   * Persist a single room to Redis immediately.
   */
  async persistRoomToRedis(code: string, attempt: number = 0): Promise<void> {
    const room = this.rooms.get(code);
    if (!room) {
      this.dirtyRooms.delete(code);
      return;
    }

    const snapshotVersion = room.version;
    try {
      const client = getRedisClient();
      const payload: PersistedRoom = {
        code: room.code,
        version: room.version,
        hostUserId: room.hostUserId,
        createdAt: room.createdAt,
        members: Array.from(room.members.values()).map((m) => ({
          ...m,
          online: false, // In Redis, online defaults to false
        })),
        defaults: room.defaults,
        queue: room.queue,
        history: room.history,
        nowPlaying: room.nowPlaying,
      };

      await client.set(
        `${REDIS_KEY_PREFIX}${code}`,
        JSON.stringify(payload),
        'EX',
        ROOM_TTL_SECONDS,
      );

      // Only clear dirty flag if room version hasn't changed during in-flight write
      const currentRoom = this.rooms.get(code);
      if (!currentRoom || currentRoom.version === snapshotVersion) {
        this.dirtyRooms.delete(code);
      }
    } catch (err) {
      logger.warn({ err, code, attempt }, 'Failed to write jam2 room to Redis (rescheduling with backoff)');
      if (attempt < 3) {
        this.scheduleRetryWrite(code, attempt);
      }
    }
  }

  /**
   * Flush all dirty rooms to Redis immediately (called on SIGTERM).
   */
  async flushAllDirty(): Promise<void> {
    for (const [code, timer] of this.debounceTimers.entries()) {
      clearTimeout(timer);
      this.debounceTimers.delete(code);
    }

    const dirtyList = Array.from(this.dirtyRooms);
    if (dirtyList.length === 0) return;

    logger.info({ count: dirtyList.length }, 'Flushing dirty jam2 rooms to Redis on shutdown');
    await Promise.allSettled(dirtyList.map((code) => this.persistRoomToRedis(code)));
  }

  /**
   * Count persisted rooms in Redis using SCAN (not KEYS).
   */
  async countPersistedRooms(): Promise<number> {
    try {
      const client = getRedisClient();
      let cursor = '0';
      let count = 0;

      do {
        const [nextCursor, keys] = await client.scan(
          cursor,
          'MATCH',
          `${REDIS_KEY_PREFIX}*`,
          'COUNT',
          100,
        );
        cursor = nextCursor;
        count += keys.length;
      } while (cursor !== '0');

      return count;
    } catch (err) {
      logger.warn({ err }, 'Could not scan Redis for existing jam2 rooms (failing open)');
      return 0;
    }
  }

  /**
   * Task B10: Advance virtual clock across multiple tracks on cold start / rehydrate.
   * If the room was playing and offline duration exceeded track duration,
   * advance track repeatedly across virtual clock to bring playback up to present.
   */
  catchUpStalePlayback(room: Room, now: number): Room {
    if (room.nowPlaying.status !== 'playing' || !room.nowPlaying.item) return room;

    let currentRoom = room;
    let advances = 0;

    while (
      currentRoom.nowPlaying.status === 'playing' &&
      currentRoom.nowPlaying.item &&
      advances < 50
    ) {
      const item = currentRoom.nowPlaying.item;
      const durationMs =
        item.song.duration > 0
          ? item.song.duration * 1000
          : Number.MAX_SAFE_INTEGER;

      const currentPos = currentRoom.nowPlaying.positionMs;
      const remainingMs = Math.max(0, durationMs - currentPos);
      const trackEndTime = currentRoom.nowPlaying.anchorServerTs + remainingMs;

      if (now >= trackEndTime) {
        // Track finished while offline: advance virtual clock to track's end timestamp
        const res = applyCommand(
          currentRoom,
          { code: currentRoom.code, type: 'trackEnded', trackSeq: currentRoom.nowPlaying.trackSeq },
          'system',
          trackEndTime,
        );
        if ('room' in res) {
          currentRoom = res.room;
          advances++;
        } else {
          break;
        }
      } else {
        // Track's remaining duration extends past now: set final position at now
        const elapsedSinceAnchor = Math.max(0, now - currentRoom.nowPlaying.anchorServerTs);
        const finalPos = Math.min(durationMs, currentRoom.nowPlaying.positionMs + elapsedSinceAnchor);
        currentRoom = {
          ...currentRoom,
          nowPlaying: {
            ...currentRoom.nowPlaying,
            positionMs: finalPos,
            anchorServerTs: now,
          },
        };
        break;
      }
    }

    return currentRoom;
  }
}

export const jam2Store = new Jam2Store();

