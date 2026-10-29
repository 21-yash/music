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

  /**
   * Return room from memory, or rehydrate from Redis if available.
   */
  async getRoom(code: string, now = Date.now()): Promise<Room | null> {
    const normalizedCode = code.trim().toUpperCase();

    // 1. Memory check
    const inMemory = this.rooms.get(normalizedCode);
    if (inMemory) {
      return inMemory;
    }

    // 2. Rehydrate from Redis
    try {
      const client = getRedisClient();
      const raw = await client.get(`${REDIS_KEY_PREFIX}${normalizedCode}`);
      if (!raw) return null;

      const parsed: PersistedRoom = JSON.parse(raw);
      const membersMap = new Map<string, Member>();
      for (const m of parsed.members) {
        membersMap.set(m.userId, {
          ...m,
          online: false, // Mark all offline on rehydration until they rejoin
        });
      }

      const rehydrated: Room = {
        code: parsed.code,
        version: parsed.version,
        hostUserId: parsed.hostUserId,
        createdAt: parsed.createdAt,
        members: membersMap,
        defaults: parsed.defaults,
        queue: parsed.queue,
        history: parsed.history,
        nowPlaying: parsed.nowPlaying,
      };

      // Catch-up stale playback if song finished while server was offline
      this.catchUpStalePlayback(rehydrated, now);

      this.rooms.set(normalizedCode, rehydrated);
      logger.info({ code: normalizedCode }, 'Rehydrated jam2 room from Redis');
      return rehydrated;
    } catch (err) {
      logger.warn({ err, code: normalizedCode }, 'Failed to rehydrate jam2 room from Redis (failing open)');
      return null;
    }
  }

  /**
   * Set or update a room in memory and schedule debounced Redis write.
   */
  setRoom(room: Room): void {
    const code = room.code.toUpperCase();
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
   * Delete room from memory and Redis.
   */
  async deleteRoom(code: string): Promise<void> {
    const normalizedCode = code.toUpperCase();
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

  /**
   * Persist a single room to Redis immediately.
   */
  async persistRoomToRedis(code: string): Promise<void> {
    const room = this.rooms.get(code);
    if (!room) {
      this.dirtyRooms.delete(code);
      return;
    }

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
      this.dirtyRooms.delete(code);
    } catch (err) {
      logger.warn({ err, code }, 'Failed to write jam2 room to Redis (failing open)');
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
   * If the room was playing and offline duration exceeded track duration + 5s,
   * advance track repeatedly (up to 50 times) to bring playback up to present.
   */
  private catchUpStalePlayback(room: Room, now: number): void {
    if (room.nowPlaying.status !== 'playing' || !room.nowPlaying.item) return;

    let advances = 0;
    while (room.nowPlaying.status === 'playing' && room.nowPlaying.item && advances < 50) {
      const durationMs =
        room.nowPlaying.item.song.duration > 0
          ? room.nowPlaying.item.song.duration * 1000
          : Number.MAX_SAFE_INTEGER;
      const elapsed = now - room.nowPlaying.anchorServerTs;
      const currentExpected = room.nowPlaying.positionMs + elapsed;

      if (currentExpected > durationMs + 5000) {
        const res = applyCommand(
          room,
          { code: room.code, type: 'trackEnded', trackSeq: room.nowPlaying.trackSeq },
          'system',
          now,
        );
        if ('room' in res) {
          Object.assign(room, res.room);
          advances++;
        } else {
          break;
        }
      } else {
        // Current track is still within bounds; adjust anchor
        room.nowPlaying.positionMs = Math.min(currentExpected, durationMs);
        room.nowPlaying.anchorServerTs = now;
        break;
      }
    }
  }
}

export const jam2Store = new Jam2Store();

