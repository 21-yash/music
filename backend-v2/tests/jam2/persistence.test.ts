import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Jam2Store } from '../../src/jam2/store';
import { Room, Song } from '../../src/jam2/types';
import * as redisConfig from '../../src/config/redis';

function makeTestSong(id: string, duration = 30): Song {
  return {
    id,
    title: `Song ${id}`,
    artists: [{ id: 'a1', name: 'Artist' }],
    duration,
    imageUrl: null,
    year: '2024',
    language: 'en',
    hasLyrics: false,
    playCount: 0,
    label: '',
    streamRef: '',
    providerId: 'jiosaavn',
  };
}

describe('Jam2 Persistence & Rehydration', () => {
  let mockRedis: Record<string, string>;
  let store: Jam2Store;

  beforeEach(() => {
    vi.useFakeTimers();
    mockRedis = {};

    vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
      get: vi.fn(async (key: string) => mockRedis[key] || null),
      set: vi.fn(async (key: string, val: string) => {
        mockRedis[key] = val;
        return 'OK';
      }),
      del: vi.fn(async (key: string) => {
        delete mockRedis[key];
        return 1;
      }),
      scan: vi.fn(async (_cursor: string) => ['0', Object.keys(mockRedis)]),
    } as any);

    store = new Jam2Store();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('debounces room persistence to Redis by 1.5s', async () => {
    const room: Room = {
      code: 'REDS',
      version: 1,
      hostUserId: 'u1',
      createdAt: 1000,
      members: new Map([
        [
          'u1',
          {
            userId: 'u1',
            username: 'User 1',
            joinedAt: 1000,
            online: true,
            lastSeenAt: 1000,
            perms: { queueAdd: true, playback: true, queueManage: true },
          },
        ],
      ]),
      defaults: { queueAdd: true, playback: false, queueManage: false },
      queue: [],
      history: [],
      nowPlaying: {
        item: null,
        status: 'idle',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 0,
      },
    };

    store.setRoom(room);
    // Immediately, not yet in Redis
    expect(mockRedis['jam2:room:REDS']).toBeUndefined();

    // Advance 1.5s
    await vi.advanceTimersByTimeAsync(1500);
    expect(mockRedis['jam2:room:REDS']).toBeDefined();

    const parsed = JSON.parse(mockRedis['jam2:room:REDS']);
    expect(parsed.code).toBe('REDS');
    // In Redis, members should be serialized with online: false
    expect(parsed.members[0].online).toBe(false);
  });

  it('flushAllDirty flushes pending rooms immediately without waiting for debounce', async () => {
    const room: Room = {
      code: 'FLSH',
      version: 1,
      hostUserId: 'u1',
      createdAt: 1000,
      members: new Map(),
      defaults: {},
      queue: [],
      history: [],
      nowPlaying: {
        item: null,
        status: 'idle',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 0,
      },
    };

    store.setRoom(room);
    expect(mockRedis['jam2:room:FLSH']).toBeUndefined();

    await store.flushAllDirty();
    expect(mockRedis['jam2:room:FLSH']).toBeDefined();
  });

  it('rehydrates room from Redis when not in memory and marks members offline', async () => {
    const persistedPayload = {
      code: 'REHY',
      version: 5,
      hostUserId: 'u_host',
      createdAt: 1000,
      members: [
        {
          userId: 'u_host',
          username: 'Host',
          joinedAt: 1000,
          online: true,
          lastSeenAt: 1000,
          perms: { queueAdd: true, playback: true, queueManage: true },
        },
      ],
      defaults: { queueAdd: true, playback: false, queueManage: false },
      queue: [],
      history: [],
      nowPlaying: {
        item: null,
        status: 'idle',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 0,
      },
    };

    mockRedis['jam2:room:REHY'] = JSON.stringify(persistedPayload);

    // Fresh store with empty memory
    const freshStore = new Jam2Store();
    expect(freshStore.hasRoom('REHY')).toBe(false);

    const rehydrated = await freshStore.getRoom('REHY');
    expect(rehydrated).not.toBeNull();
    expect(rehydrated?.code).toBe('REHY');
    expect(rehydrated?.version).toBe(1005); // A5: version bumped by += 1000 on rehydrate
    expect(rehydrated?.members.get('u_host')?.online).toBe(false); // Offline until reconnect
    expect(freshStore.hasRoom('REHY')).toBe(true);
  });

  it('advances stale playback during rehydration if server was offline past track end', async () => {
    const s1 = makeTestSong('song_1', 10); // 10s duration = 10,000ms
    const s2 = makeTestSong('song_2', 30); // 30s duration

    const persistedPayload = {
      code: 'STAL',
      version: 1,
      hostUserId: 'u_host',
      createdAt: 1000,
      members: [],
      defaults: {},
      queue: [
        {
          qid: 'q_2',
          song: s2,
          addedByUserId: 'u_host',
          addedAt: 1000,
          recommended: false,
        },
      ],
      history: [],
      nowPlaying: {
        item: {
          qid: 'q_1',
          song: s1,
          addedByUserId: 'u_host',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000, // started at t=1000
        trackSeq: 1,
      },
    };

    mockRedis['jam2:room:STAL'] = JSON.stringify(persistedPayload);

    // Rehydrate at t=20,000 (19 seconds later, > 10s + 5s)
    const freshStore = new Jam2Store();
    const rehydrated = await freshStore.getRoom('STAL', 20000);

    expect(rehydrated).not.toBeNull();
    // song_1 should have finished and advanced to song_2!
    expect(rehydrated?.nowPlaying.item?.song.id).toBe('song_2');
    expect(rehydrated?.nowPlaying.trackSeq).toBe(2);
    expect(rehydrated?.history[0].song.id).toBe('song_1');
    expect(rehydrated?.queue.length).toBe(0);
  });

  it('fails open when Redis throws an error', async () => {
    vi.spyOn(redisConfig, 'getRedisClient').mockImplementation(() => {
      throw new Error('Redis connection refused');
    });

    const brokenStore = new Jam2Store();

    // In-memory set still works
    const room: Room = {
      code: 'FAIL',
      version: 1,
      hostUserId: 'u1',
      createdAt: 1000,
      members: new Map(),
      defaults: {},
      queue: [],
      history: [],
      nowPlaying: {
        item: null,
        status: 'idle',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 0,
      },
    };

    brokenStore.setRoom(room);
    expect(brokenStore.hasRoom('FAIL')).toBe(true);

    // Advance debounce: should not throw unhandled exception
    await vi.advanceTimersByTimeAsync(2000);

    // getRoom for non-existent room fails open returning null
    const result = await brokenStore.getRoom('NONE');
    expect(result).toBeNull();

    // deleteRoom fails open
    await expect(brokenStore.deleteRoom('FAIL')).resolves.not.toThrow();
  });

  describe('Tombstone and Resurrection Prevention (Task B2)', () => {
    it('interleaved deleteRoom and getRoom prevents resurrection from Redis', async () => {
      const persistedPayload = {
        code: 'DEAD',
        version: 5,
        hostUserId: 'u1',
        createdAt: 1000,
        members: [{ userId: 'u1', username: 'U1', joinedAt: 1000, online: false, lastSeenAt: 1000, perms: {} }],
        defaults: {},
        queue: [],
        history: [],
        nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
      };
      mockRedis['jam2:room:DEAD'] = JSON.stringify(persistedPayload);

      let resolveRedisGet: (val: string | null) => void;
      const slowGetPromise = new Promise<string | null>((resolve) => {
        resolveRedisGet = resolve;
      });

      const client = redisConfig.getRedisClient();
      vi.spyOn(client, 'get').mockImplementation(async (key: string) => {
        if (key === 'jam2:room:DEAD') {
          return slowGetPromise;
        }
        return mockRedis[key] || null;
      });

      // 1. In-flight getRoom started
      const getRoomPromise = store.getRoom('DEAD');

      // 2. Interleaved deleteRoom happens before getRoom finishes
      await store.deleteRoom('DEAD');

      // 3. Redis GET finally resolves with the stale data
      resolveRedisGet!(JSON.stringify(persistedPayload));
      const result = await getRoomPromise;

      // Must be null (not resurrected!)
      expect(result).toBeNull();
      expect(store.hasRoom('DEAD')).toBe(false);
    });

    it('tombstone blocks Redis rehydration within 60s window', async () => {
      const persistedPayload = {
        code: 'TOMB',
        version: 2,
        hostUserId: 'u1',
        createdAt: 1000,
        members: [],
        defaults: {},
        queue: [],
        history: [],
        nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
      };
      mockRedis['jam2:room:TOMB'] = JSON.stringify(persistedPayload);

      // deleteRoom tombstones for 60s
      await store.deleteRoom('TOMB');

      // Subsequent getRoom within 60s returns null
      const res = await store.getRoom('TOMB');
      expect(res).toBeNull();
      expect(store.hasRoom('TOMB')).toBe(false);
    });

    it('tombstone only blocks Redis rehydration; rooms in memory are unaffected', async () => {
      const room: Room = {
        code: 'ALIVE',
        version: 1,
        hostUserId: 'u1',
        createdAt: 1000,
        members: new Map(),
        defaults: {},
        queue: [],
        history: [],
        nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
      };

      // Put in memory
      store.setRoom(room);

      // In-memory get returns immediately
      const inMem = await store.getRoom('ALIVE');
      expect(inMem).toBe(room);
    });
  });

  describe('Persistence Correctness & Retries (Task B3)', () => {
    it('retries failed persistRoomToRedis with backoff', async () => {
      const room: Room = {
        code: 'RTRY',
        version: 1,
        hostUserId: 'u1',
        createdAt: 1000,
        members: new Map(),
        defaults: {},
        queue: [],
        history: [],
        nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
      };
      store.setRoom(room);
      expect(store.isRoomDirty('RTRY')).toBe(true);

      const client = redisConfig.getRedisClient();
      let callCount = 0;
      vi.spyOn(client, 'set').mockImplementation(async () => {
        callCount++;
        if (callCount === 1) {
          throw new Error('Redis write failure 1');
        }
        return 'OK';
      });

      // Initial write
      await store.persistRoomToRedis('RTRY', 0);
      expect(callCount).toBe(1);
      // Room must still be dirty because write failed
      expect(store.isRoomDirty('RTRY')).toBe(true);

      // Advance timers by backoff delay (500ms)
      await vi.advanceTimersByTimeAsync(500);

      // Retry should have executed and succeeded
      expect(callCount).toBe(2);
      expect(store.isRoomDirty('RTRY')).toBe(false);
    });

    it('preserves dirty flag if room version is mutated during in-flight write', async () => {
      const room: Room = {
        code: 'MUTE',
        version: 1,
        hostUserId: 'u1',
        createdAt: 1000,
        members: new Map(),
        defaults: {},
        queue: [],
        history: [],
        nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
      };
      store.setRoom(room);
      expect(store.isRoomDirty('MUTE')).toBe(true);

      let resolveWrite: (val: string) => void;
      const slowWritePromise = new Promise<string>((resolve) => {
        resolveWrite = resolve;
      });

      const client = redisConfig.getRedisClient();
      vi.spyOn(client, 'set').mockImplementation(async (key: string) => {
        if (key.includes('MUTE')) {
          return slowWritePromise;
        }
        return 'OK';
      });

      // Start in-flight write with version 1
      const writePromise = store.persistRoomToRedis('MUTE');

      // While in flight, room is mutated to version 2
      room.version = 2;

      // In-flight write completes
      resolveWrite!('OK');
      await writePromise;

      // Dirty flag MUST be preserved because version was bumped during write!
      expect(store.isRoomDirty('MUTE')).toBe(true);
    });
  });

  describe('Task B10: catchUpStalePlayback multi-track advance', () => {
    it('advances virtual clock across multiple short tracks during long downtime', () => {
      const song1 = makeTestSong('song_1', 30); // 30s
      const song2 = makeTestSong('song_2', 40); // 40s
      const song3 = makeTestSong('song_3', 50); // 50s

      const initialAnchor = 10_000;
      const room: Room = {
        code: 'MULT',
        version: 1,
        hostUserId: 'u1',
        createdAt: initialAnchor,
        members: new Map(),
        defaults: {},
        queue: [
          { qid: 'q_2', song: song2, addedByUserId: 'u1', addedAt: initialAnchor, recommended: false },
          { qid: 'q_3', song: song3, addedByUserId: 'u1', addedAt: initialAnchor, recommended: false },
        ],
        history: [],
        nowPlaying: {
          item: { qid: 'q_1', song: song1, addedByUserId: 'u1', addedAt: initialAnchor, recommended: false },
          status: 'playing',
          positionMs: 0,
          anchorServerTs: initialAnchor,
          trackSeq: 1,
        },
      };

      // Server offline from t=10_000 until t=100_000 (90 seconds elapsed)
      // Track 1 (30s) finishes at 40_000
      // Track 2 (40s) finishes at 80_000
      // Track 3 (50s) should be playing at 20s (20_000ms) into the song at now=100_000
      const now = 100_000;
      const caughtUp = store.catchUpStalePlayback(room, now);

      expect(caughtUp.nowPlaying.status).toBe('playing');
      expect(caughtUp.nowPlaying.item?.song.id).toBe('song_3');
      expect(caughtUp.nowPlaying.positionMs).toBe(20_000);
      expect(caughtUp.nowPlaying.anchorServerTs).toBe(now);
      expect(caughtUp.nowPlaying.trackSeq).toBe(3);

      // Queue should have 0 items left (all popped)
      expect(caughtUp.queue.length).toBe(0);

      // History should contain song_1 and song_2
      expect(caughtUp.history.length).toBe(2);
      expect(caughtUp.history[0].song.id).toBe('song_1');
      expect(caughtUp.history[1].song.id).toBe('song_2');
    });

    it('transitions to idle when downtime exceeds all queued tracks', () => {
      const song1 = makeTestSong('song_1', 30); // 30s
      const song2 = makeTestSong('song_2', 40); // 40s

      const initialAnchor = 10_000;
      const room: Room = {
        code: 'IDLE',
        version: 1,
        hostUserId: 'u1',
        createdAt: initialAnchor,
        members: new Map(),
        defaults: {},
        queue: [
          { qid: 'q_2', song: song2, addedByUserId: 'u1', addedAt: initialAnchor, recommended: false },
        ],
        history: [],
        nowPlaying: {
          item: { qid: 'q_1', song: song1, addedByUserId: 'u1', addedAt: initialAnchor, recommended: false },
          status: 'playing',
          positionMs: 0,
          anchorServerTs: initialAnchor,
          trackSeq: 1,
        },
      };

      // Total queue duration = 30s + 40s = 70s. Finishes at 80_000.
      // Rehydrate at now=150_000 (70s after queue ended)
      const now = 150_000;
      const caughtUp = store.catchUpStalePlayback(room, now);

      expect(caughtUp.nowPlaying.status).toBe('idle');
      expect(caughtUp.nowPlaying.item).toBeNull();
      expect(caughtUp.queue.length).toBe(0);
      expect(caughtUp.history.length).toBe(2);
      expect(caughtUp.history[0].song.id).toBe('song_1');
      expect(caughtUp.history[1].song.id).toBe('song_2');
    });
  });
});

