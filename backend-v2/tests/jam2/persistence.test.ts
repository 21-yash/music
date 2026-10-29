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
    expect(rehydrated?.version).toBe(5);
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
});

