import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Jam2Store } from '../../src/jam2/store';
import { Jam2Manager, runSafely } from '../../src/jam2/manager';
import * as redisConfig from '../../src/config/redis';
import { Room } from '../../src/jam2/types';

describe('Jam2 Resilience & Fault Tolerance', () => {
  let unhandledRejections: unknown[] = [];
  let onUnhandledRejection: (reason: unknown) => void;

  beforeEach(() => {
    unhandledRejections = [];
    onUnhandledRejection = (reason: unknown) => {
      unhandledRejections.push(reason);
    };
    process.on('unhandledRejection', onUnhandledRejection);
  });

  afterEach(() => {
    process.off('unhandledRejection', onUnhandledRejection);
    vi.restoreAllMocks();
  });

  it('runSafely catches errors in async functions without unhandledRejection', async () => {
    runSafely('test_fault_async', async () => {
      throw new Error('Async failure simulation');
    });

    await new Promise((r) => setTimeout(r, 20));
    expect(unhandledRejections.length).toBe(0);
  });

  it('runSafely catches errors in sync functions without throwing', () => {
    expect(() => {
      runSafely('test_fault_sync', () => {
        throw new Error('Sync failure simulation');
      });
    }).not.toThrow();
    expect(unhandledRejections.length).toBe(0);
  });

  it('corrupted JSON in Redis is handled gracefully and fails open', async () => {
    vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
      get: vi.fn(async () => 'INVALID_JSON{{{'),
    } as any);

    const store = new Jam2Store();
    const room = await store.getRoom('CORRUPT');
    expect(room).toBeNull();
    expect(unhandledRejections.length).toBe(0);
  });

  it('Redis write failure does not crash the server and fails open', async () => {
    vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
      set: vi.fn(async () => {
        throw new Error('Redis OUT OF MEMORY');
      }),
    } as any);

    const store = new Jam2Store();
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

    store.setRoom(room);
    await store.persistRoomToRedis('FAIL');
    expect(unhandledRejections.length).toBe(0);
  });

  it('failing recommendation provider never bubbles unhandled rejection or stops manager', async () => {
    const store = new Jam2Store();
    const failingFetcher = vi.fn().mockImplementation(async () => {
      throw new Error('Recommendation upstream timeout 504');
    });

    const manager = new Jam2Manager(store, failingFetcher);
    const room = await manager.createRoom('u1', 'User 1');

    await manager.executeCommand(
      room.code,
      {
        code: room.code,
        type: 'queueAdd',
        song: {
          id: 'test_song',
          title: 'Title',
          artists: [],
          duration: 100,
          imageUrl: null,
          year: '',
          language: '',
          hasLyrics: false,
          playCount: 0,
          label: '',
          streamRef: '',
          providerId: 'jiosaavn',
        },
      },
      'u1',
    );

    await new Promise((r) => setTimeout(r, 50));
    expect(unhandledRejections.length).toBe(0);

    const freshRoom = await store.getRoom(room.code);
    expect(freshRoom?.nowPlaying.status).toBe('playing');
    manager.stop();
  });
});

