import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Jam2Manager, runSafely } from '../../src/jam2/manager';
import { Jam2Store } from '../../src/jam2/store';
import { Song } from '../../src/jam2/types';

function makeSong(id: string, duration = 10): Song {
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

describe('Jam2Manager', () => {
  let store: Jam2Store;
  let mockFetcher: (
    songId: string,
    providerId?: string,
    seedTitle?: string,
    seedArtist?: string,
  ) => Promise<Song[]>;
  let manager: Jam2Manager;
  let emittedEvents: Array<{ event: string; payload: unknown }>;

  beforeEach(() => {
    vi.useFakeTimers();
    store = new Jam2Store();
    emittedEvents = [];

    mockFetcher = vi.fn().mockResolvedValue([makeSong('rec_1'), makeSong('rec_2')]);

    manager = new Jam2Manager(store, mockFetcher);

    // Mock IO
    const mockIO = {
      to: (_room: string) => ({
        emit: (event: string, payload: unknown) => {
          emittedEvents.push({ event, payload });
        },
      }),
      emit: (event: string, payload: unknown) => {
        emittedEvents.push({ event, payload });
      },
    } as any;
    manager.setIO(mockIO);
  });

  afterEach(() => {
    manager.stop();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  describe('Room Lifecycle & Code Generation', () => {
    it('creates room with 4-character code and host as member', async () => {
      const room = await manager.createRoom('user_h', 'HostUser');
      expect(room.code.length).toBe(4);
      expect(room.hostUserId).toBe('user_h');
      expect(room.members.get('user_h')?.perms.playback).toBe(true);
      expect(store.hasRoom(room.code)).toBe(true);
    });

    it('joins existing room and assigns default permissions', async () => {
      const room = await manager.createRoom('user_h', 'HostUser');
      const res = await manager.joinRoom(room.code, 'user_g', 'GuestUser');

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.members.has('user_g')).toBe(true);
        expect(res.room.members.get('user_g')?.perms.queueAdd).toBe(true);
        expect(res.room.members.get('user_g')?.perms.playback).toBe(false);
      }
    });

    it('returns NOT_FOUND for non-existent room', async () => {
      const res = await manager.joinRoom('NONE', 'user_g', 'GuestUser');
      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('NOT_FOUND');
      }
    });
  });

  describe('Silent Host Drop & 20s Grace Period Handover', () => {
    it('transfers host to earliest-joined online member after 20s offline', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');
      manager.registerSocket('sock_host', room.code, 'host_1', 'HostUser');

      // Guest 1 joins
      await manager.joinRoom(room.code, 'guest_1', 'GuestOne');
      manager.registerSocket('sock_guest1', room.code, 'guest_1', 'GuestOne');

      // Guest 2 joins 5s later
      await vi.advanceTimersByTimeAsync(5000);
      await manager.joinRoom(room.code, 'guest_2', 'GuestTwo');
      manager.registerSocket('sock_guest2', room.code, 'guest_2', 'GuestTwo');

      // Host socket drops silently (e.g. app kill, network drop)
      manager.handleSocketDisconnect('sock_host');
      await vi.advanceTimersByTimeAsync(0);

      const roomAfterDrop = await store.getRoom(room.code);
      expect(roomAfterDrop?.members.get('host_1')?.online).toBe(false);
      expect(roomAfterDrop?.hostUserId).toBe('host_1'); // still host during grace period

      // Advance time by 19 seconds -> host should still NOT be changed
      await vi.advanceTimersByTimeAsync(19000);
      let midRoom = await store.getRoom(room.code);
      expect(midRoom?.hostUserId).toBe('host_1');

      // Advance 2 more seconds (total 21s) -> 20s timer expires
      await vi.advanceTimersByTimeAsync(2000);

      const updatedRoom = await store.getRoom(room.code);
      expect(updatedRoom?.hostUserId).toBe('guest_1'); // Earliest joined online guest

      const hostChangedEvent = emittedEvents.find(
        (e) => e.event === 'jam2:event' && (e.payload as any).kind === 'host_changed',
      );
      expect(hostChangedEvent).toBeDefined();
      expect((hostChangedEvent?.payload as any).userId).toBe('guest_1');
    });

    it('cancels grace timer if host rejoins within 20s', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');
      manager.registerSocket('sock_host1', room.code, 'host_1', 'HostUser');

      await manager.joinRoom(room.code, 'guest_1', 'GuestOne');
      manager.registerSocket('sock_guest1', room.code, 'guest_1', 'GuestOne');

      // Host drops
      manager.handleSocketDisconnect('sock_host1');
      await vi.advanceTimersByTimeAsync(0);

      // Host returns at 15 seconds
      await vi.advanceTimersByTimeAsync(15000);
      await manager.joinRoom(room.code, 'host_1', 'HostUser');
      manager.registerSocket('sock_host2', room.code, 'host_1', 'HostUser');

      // Advance past 20s
      await vi.advanceTimersByTimeAsync(10000);

      const freshRoom = await store.getRoom(room.code);
      expect(freshRoom?.hostUserId).toBe('host_1'); // Host remained host
    });
  });

  describe('Explicit Leave & Room End', () => {
    it('transfers host immediately when host clicks Leave', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');
      await manager.joinRoom(room.code, 'guest_1', 'GuestOne');
      manager.registerSocket('sock_guest1', room.code, 'guest_1', 'GuestOne');

      // Host explicitly leaves
      const res = await manager.leaveRoom(room.code, 'host_1');
      expect(res.success).toBe(true);

      const updatedRoom = await store.getRoom(room.code);
      expect(updatedRoom?.hostUserId).toBe('guest_1');
      expect(updatedRoom?.members.has('host_1')).toBe(false);
    });

    it('destroys room when host ends jam', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');
      await manager.joinRoom(room.code, 'guest_1', 'GuestOne');

      const res = await manager.endRoom(room.code, 'host_1');
      expect(res.success).toBe(true);

      expect(store.hasRoom(room.code)).toBe(false);
      const closedEvent = emittedEvents.find((e) => e.event === 'jam2:closed');
      expect(closedEvent).toBeDefined();
      expect((closedEvent?.payload as any).reason).toBe('ended');
    });

    it('rejects guest from ending room', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');
      await manager.joinRoom(room.code, 'guest_1', 'GuestOne');

      const res = await manager.endRoom(room.code, 'guest_1');
      expect(res.success).toBe(false);
      expect(res.error?.code).toBe('FORBIDDEN');
    });
  });

  describe('Track Fallback Timer', () => {
    it('automatically triggers trackEnded when track duration + 5s expires', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');

      // Queue and start a 10s song
      await manager.executeCommand(
        room.code,
        {
          code: room.code,
          type: 'queueAdd',
          song: makeSong('song_10s', 10), // 10 seconds duration
        },
        'host_1',
      );

      // Add a second song to queue
      await manager.executeCommand(
        room.code,
        {
          code: room.code,
          type: 'queueAdd',
          song: makeSong('song_2', 30),
        },
        'host_1',
      );

      const r1 = await store.getRoom(room.code);
      expect(r1?.nowPlaying.item?.song.id).toBe('song_10s');
      expect(r1?.nowPlaying.status).toBe('playing');

      // Advance by 15.5s (10s duration + 5s grace + 0.5s)
      await vi.advanceTimersByTimeAsync(15500);
      await Promise.resolve();

      const r2 = await store.getRoom(room.code);
      expect(r2?.nowPlaying.item?.song.id).toBe('song_2');
      expect(r2?.nowPlaying.trackSeq).toBe(2);
      expect(r2?.history[0].song.id).toBe('song_10s');
    });
  });

  describe('Recommendations Side-Effect', () => {
    it('triggers recommendation fetch when user queue has <= 1 item', async () => {
      const room = await manager.createRoom('host_1', 'HostUser');

      // Add one song -> auto plays
      await manager.executeCommand(
        room.code,
        {
          code: room.code,
          type: 'queueAdd',
          song: makeSong('seed_song', 60),
        },
        'host_1',
      );

      await vi.advanceTimersByTimeAsync(100);

      expect(mockFetcher).toHaveBeenCalledWith('seed_song', 'jiosaavn', 'Song seed_song', 'Artist');

      const freshRoom = await store.getRoom(room.code);
      expect(freshRoom?.queue.length).toBe(2);
      expect(freshRoom?.queue[0].recommended).toBe(true);
      expect(freshRoom?.queue[0].song.id).toBe('rec_1');
    });

    it('swallows fetch error without breaking room state', async () => {
      const failingFetcher = vi.fn().mockRejectedValue(new Error('Provider network down'));
      const mgr = new Jam2Manager(store, failingFetcher);

      const room = await mgr.createRoom('host_1', 'HostUser');
      await mgr.executeCommand(
        room.code,
        {
          code: room.code,
          type: 'queueAdd',
          song: makeSong('seed_song', 60),
        },
        'host_1',
      );

      await vi.advanceTimersByTimeAsync(100);

      const freshRoom = await store.getRoom(room.code);
      expect(freshRoom?.nowPlaying.item?.song.id).toBe('seed_song');
      expect(freshRoom?.nowPlaying.status).toBe('playing');
      expect(freshRoom?.queue.length).toBe(0); // recs failed cleanly
      mgr.stop();
    });
  });

  describe('Rate Limiters', () => {
    it('limits commands to 20 per 10s', () => {
      const key = 'user_ratelimit_test';
      for (let i = 0; i < 20; i++) {
        expect(manager.checkCmdRateLimit(key, 20, 10000)).toBe(true);
      }
      expect(manager.checkCmdRateLimit(key, 20, 10000)).toBe(false);

      // Advance 10s -> resets
      vi.advanceTimersByTime(10001);
      expect(manager.checkCmdRateLimit(key, 20, 10000)).toBe(true);
    });

    it('limits failed join attempts to 5 per minute', () => {
      const user = 'join_fail_user';
      for (let i = 0; i < 5; i++) {
        expect(manager.checkJoinFailRateLimit(user, 5, 60000)).toBe(true);
        manager.recordJoinFailure(user, 60000);
      }
      expect(manager.checkJoinFailRateLimit(user, 5, 60000)).toBe(false);

      vi.advanceTimersByTime(60001);
      expect(manager.checkJoinFailRateLimit(user, 5, 60000)).toBe(true);
    });

    it('limits time pings to 30 per minute', () => {
      const sock = 'sock_ping';
      for (let i = 0; i < 30; i++) {
        expect(manager.checkTimeRateLimit(sock, 30, 60000)).toBe(true);
      }
      expect(manager.checkTimeRateLimit(sock, 30, 60000)).toBe(false);
    });
  });

  describe('runSafely Utility', () => {
    it('catches synchronous exceptions without throwing', () => {
      expect(() => {
        runSafely('test_sync_throw', () => {
          throw new Error('Explosion');
        });
      }).not.toThrow();
    });

    it('catches asynchronous rejections without unhandled rejection', async () => {
      let threw = false;
      runSafely('test_async_throw', async () => {
        threw = true;
        throw new Error('Async explosion');
      });
      await vi.advanceTimersByTimeAsync(0);
      expect(threw).toBe(true);
    });
  });
});
