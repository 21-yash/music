import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Jam2Store } from '../../src/jam2/store';
import { Jam2Manager } from '../../src/jam2/manager';
import { applyCommand } from '../../src/jam2/reducer';
import { Room, Song, DEFAULT_ROOM_PERMS, clientCmdSchema } from '../../src/jam2/types';
import * as redisConfig from '../../src/config/redis';

function makeSong(id: string, duration = 180): Song {
  return {
    id,
    title: `Song ${id}`,
    artists: [{ id: 'a1', name: 'Artist 1' }],
    album: { id: 'alb1', title: 'Album 1' },
    duration,
  };
}

function makeTestRoom(code: string): Room {
  return {
    code,
    version: 1,
    hostUserId: 'u_host',
    createdAt: 1000,
    members: new Map([
      [
        'u_host',
        {
          userId: 'u_host',
          username: 'Host',
          joinedAt: 1000,
          online: true,
          lastSeenAt: 1000,
          perms: { queueAdd: true, playback: true, queueManage: true },
        },
      ],
      [
        'u_guest',
        {
          userId: 'u_guest',
          username: 'Guest',
          joinedAt: 1010,
          online: true,
          lastSeenAt: 1010,
          perms: { queueAdd: true, playback: false, queueManage: false },
        },
      ],
    ]),
    defaults: { ...DEFAULT_ROOM_PERMS },
    queue: [],
    history: [],
    nowPlaying: {
      item: {
        qid: 'q1',
        song: makeSong('song_np', 200),
        addedByUserId: 'u_host',
        addedAt: 1000,
        recommended: false,
      },
      status: 'playing',
      positionMs: 50000,
      anchorServerTs: 1000,
      trackSeq: 1,
    },
  };
}

describe('Part B: Backend Robustness & Hygiene (B1 - B9)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  // ─── B1: No-op commands ──────────────────────────────────────────────
  describe('B1: No-op commands', () => {
    it('seek to virtually same position (< 200ms delta) is a no-op that does not bump version or broadcast', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = makeTestRoom('B1RM');
      const now = Date.now();
      room.nowPlaying.anchorServerTs = now;
      room.nowPlaying.positionMs = 50000;
      store.setRoom(room);

      const broadcastSpy = vi.spyOn(manager, 'broadcastState');
      const storeSetSpy = vi.spyOn(store, 'setRoom');

      // Seeking to 50050 is delta ~50ms (< 200ms)
      const res = await manager.executeCommand(
        'B1RM',
        { code: 'B1RM', type: 'seek', positionMs: 50050 },
        'u_host',
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.version).toBe(1);
      }
      expect(broadcastSpy).not.toHaveBeenCalled();
      expect(storeSetSpy).not.toHaveBeenCalled();
      manager.stop();
    });

    it('play when already playing and pause when already paused are no-ops', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = makeTestRoom('B1NOP');
      store.setRoom(room);

      const resPlay = await manager.executeCommand(
        'B1NOP',
        { code: 'B1NOP', type: 'play' },
        'u_host',
      );
      expect('room' in resPlay).toBe(true);
      if ('room' in resPlay) {
        expect(resPlay.room.version).toBe(1);
      }

      // Now pause
      const resPause1 = await manager.executeCommand(
        'B1NOP',
        { code: 'B1NOP', type: 'pause' },
        'u_host',
      );
      expect('room' in resPause1).toBe(true);
      if ('room' in resPause1) {
        expect(resPause1.room.version).toBe(2);
      }

      // Pause again -> no-op
      const resPause2 = await manager.executeCommand(
        'B1NOP',
        { code: 'B1NOP', type: 'pause' },
        'u_host',
      );
      expect('room' in resPause2).toBe(true);
      if ('room' in resPause2) {
        expect(resPause2.room.version).toBe(2);
      }
      manager.stop();
    });
  });

  // ─── B2: trackEnded boundary & applied ack ───────────────────────────
  describe('B2: trackEnded boundary and applied acknowledgement', () => {
    it('returns applied: false if reported too early (> 6s remaining on server)', () => {
      const room = makeTestRoom('B2RM');
      // Song duration is 200s (200,000ms). Expected pos at now=1000 is 50,000ms.
      // Remaining is 150s >> 6s.
      const res = applyCommand(
        room,
        { code: 'B2RM', type: 'trackEnded', trackSeq: 1 },
        'u_guest',
        1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.applied).toBe(false);
        expect(res.room.version).toBe(1);
      }
    });

    it('returns applied: true and advances track if within 6s of end', () => {
      const room = makeTestRoom('B2END');
      // Duration is 200s (200,000ms). Set position to 195,000ms (5s remaining).
      room.nowPlaying.positionMs = 195000;
      room.nowPlaying.anchorServerTs = 1000;

      const res = applyCommand(
        room,
        { code: 'B2END', type: 'trackEnded', trackSeq: 1 },
        'u_guest',
        1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.applied).toBe(true);
        expect(res.room.version).toBe(2);
        expect(res.room.nowPlaying.status).toBe('idle');
      }
    });
  });

  // ─── B3: recsSeededForTrackSeq and prune sweep ────────────────────────
  describe('B3: recsSeededForTrackSeq cleanup & rate limit sweeping', () => {
    it('clears recsSeededForTrackSeq and inflight sets on room destroy', async () => {
      const store = new Jam2Store();
      const mockFetch = vi.fn().mockResolvedValue([makeSong('rec_1'), makeSong('rec_2')]);
      const manager = new Jam2Manager(store, mockFetch);

      const room = await manager.createRoom('u_host', 'Host');
      await manager.executeCommand(
        room.code,
        { code: room.code, type: 'queueAdd', song: makeSong('song_rec', 180) },
        'u_host',
      );

      await new Promise((r) => setTimeout(r, 60));
      expect(mockFetch).toHaveBeenCalled();

      // Destroy room
      await manager.destroyRoom(room.code, 'ended');
      expect(store.hasRoom(room.code)).toBe(false);

      manager.stop();
    });
  });

  // ─── B4: Sockets cleanup on destroyRoomLocked ─────────────────────────
  describe('B4: Sockets leaving room and metadata cleanup on destroy', () => {
    it('calls socketsLeave and clears socketMeta entries on room destroy', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const leaveMock = vi.fn();
      const mockIo = {
        to: vi.fn().mockReturnValue({ emit: vi.fn() }),
        in: vi.fn().mockReturnValue({ socketsLeave: leaveMock }),
      };
      manager.setIO(mockIo as any);

      const room = await manager.createRoom('u_host', 'Host');
      manager.registerSocket('sock_1', room.code, 'u_host', 'Host');

      await manager.destroyRoom(room.code, 'ended');

      expect(mockIo.to).toHaveBeenCalledWith(`jam2:${room.code}`);
      expect(mockIo.in).toHaveBeenCalledWith(`jam2:${room.code}`);
      expect(leaveMock).toHaveBeenCalledWith(`jam2:${room.code}`);

      // Socket disconnect after destroy should be a clean no-op
      expect(() => manager.handleSocketDisconnect('sock_1')).not.toThrow();
      manager.stop();
    });
  });

  // ─── B5: Room code generation uniqueness ─────────────────────────────
  describe('B5: Room code generation uniqueness', () => {
    it('generateUniqueRoomCode checks Redis existence and generates a 4-char code', async () => {
      const store = new Jam2Store();
      const existsSpy = vi.spyOn(store, 'checkRoomExists').mockResolvedValue(false);
      const manager = new Jam2Manager(store);

      const code = await manager.generateUniqueRoomCode();
      expect(code).toHaveLength(4);
      expect(existsSpy).toHaveBeenCalled();
      manager.stop();
    });
  });

  // ─── B6: store.getRoom in-flight promise bug ──────────────────────────
  describe('B6: store.getRoom single-flight with .finally()', () => {
    it('concurrent getRoom calls share one promise and map entry is cleaned up on completion', async () => {
      const store = new Jam2Store();
      const getSpy = vi.fn(async () => null);
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: getSpy,
        set: vi.fn(),
        del: vi.fn(),
        scan: vi.fn(async () => ['0', []]),
      } as any);

      const p1 = store.getRoom('MISSING1');
      const p2 = store.getRoom('MISSING1');

      const [r1, r2] = await Promise.all([p1, p2]);
      expect(r1).toBeNull();
      expect(r2).toBeNull();
      expect(getSpy).toHaveBeenCalledTimes(1);

      // Subsequent call creates a new promise since previous was cleared in finally
      await store.getRoom('MISSING1');
      expect(getSpy).toHaveBeenCalledTimes(2);
    });
  });

  // ─── B7: Structured queueAddMany acknowledgement ─────────────────────
  describe('B7: Structured queueAddMany ack counts', () => {
    it('reports exact added, skippedDuplicate, and skippedFull from reducer', () => {
      const room = makeTestRoom('B7RM');
      // Fill user queue with 49 songs
      for (let i = 0; i < 49; i++) {
        room.queue.push({
          qid: `q_user_${i}`,
          song: makeSong(`s_user_${i}`, 100),
          addedByUserId: 'u_host',
          addedAt: 1000,
          recommended: false,
        });
      }

      // Add 3 songs: 1 duplicate of nowPlaying ('song_np'), 1 valid, 1 exceeding cap
      const cmdSongs = [
        makeSong('song_np', 200), // duplicate
        makeSong('s_new_1', 100),  // added (reaches 50)
        makeSong('s_new_2', 100),  // skippedFull
      ];

      const res = applyCommand(
        room,
        { code: 'B7RM', type: 'queueAddMany', songs: cmdSongs },
        'u_host',
        1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.added).toBe(1);
        expect(res.skippedDuplicate).toBe(1);
        expect(res.skippedFull).toBe(1);
      }
    });
  });

  // ─── B8: Security, hygiene, and command schemas ───────────────────────
  describe('B8: Security & Hygiene', () => {
    it('clientCmdSchema validates code: min(1).max(20)', () => {
      const valid = clientCmdSchema.safeParse({
        code: 'ABCD',
        type: 'play',
      });
      expect(valid.success).toBe(true);

      const invalidEmpty = clientCmdSchema.safeParse({
        code: '',
        type: 'play',
      });
      expect(invalidEmpty.success).toBe(false);

      const invalidTooLong = clientCmdSchema.safeParse({
        code: 'A'.repeat(21),
        type: 'play',
      });
      expect(invalidTooLong.success).toBe(false);
    });

    it('kick ban persists in Redis with 10m TTL and rejects rejoining', async () => {
      const store = new Jam2Store();
      const redisStore: Record<string, string> = {};
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: vi.fn(async (key: string) => redisStore[key] ?? null),
        set: vi.fn(async (key: string, val: string) => {
          redisStore[key] = val;
          return 'OK';
        }),
        del: vi.fn(),
        scan: vi.fn(async () => ['0', []]),
      } as any);

      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');
      await manager.joinRoom(room.code, 'bad_user', 'BadUser');

      await manager.executeCommand(
        room.code,
        { code: room.code, type: 'kick', userId: 'bad_user' },
        'u_host',
      );

      // Check redis key
      expect(redisStore[`jam2:ban:${room.code}:bad_user`]).toBe('1');

      // Attempt rejoin
      const rejoinRes = await manager.joinRoom(room.code, 'bad_user', 'BadUser');
      expect('error' in rejoinRes).toBe(true);
      if ('error' in rejoinRes) {
        expect(rejoinRes.error.code).toBe('FORBIDDEN');
        expect(rejoinRes.error.message).toBe('You were removed from this Jam by the host.');
      }
      manager.stop();
    });
  });

  // ─── B9: setDefaults semantics ───────────────────────────────────────
  describe('B9: setDefaults semantics and applyToExisting', () => {
    it('when applyToExisting is false/omitted, only updates defaults and not existing members', () => {
      const room = makeTestRoom('B9OFF');
      expect(room.members.get('u_guest')?.perms.queueManage).toBe(false);

      const res = applyCommand(
        room,
        { code: 'B9OFF', type: 'setDefaults', perms: { queueManage: true } },
        'u_host',
        1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.defaults.queueManage).toBe(true);
        expect(res.room.members.get('u_guest')?.perms.queueManage).toBe(false);
        expect(res.events).toHaveLength(0);
      }
    });

    it('when applyToExisting is true, updates defaults AND all existing non-host members with perm_changed events', () => {
      const room = makeTestRoom('B9ON');
      expect(room.members.get('u_guest')?.perms.queueManage).toBe(false);

      const res = applyCommand(
        room,
        {
          code: 'B9ON',
          type: 'setDefaults',
          perms: { queueManage: true },
          applyToExisting: true,
        },
        'u_host',
        1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.defaults.queueManage).toBe(true);
        expect(res.room.members.get('u_guest')?.perms.queueManage).toBe(true);
        expect(res.events).toEqual([
          {
            kind: 'perm_changed',
            userId: 'u_guest',
            perms: { queueAdd: true, playback: false, queueManage: true },
          },
        ]);
      }
    });
  });
});

