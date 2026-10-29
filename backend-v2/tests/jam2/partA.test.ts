import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { applyCommand } from '../../src/jam2/reducer';
import { Jam2Manager, KeyedMutex } from '../../src/jam2/manager';
import { Jam2Store } from '../../src/jam2/store';
import { Room, Song, DEFAULT_ROOM_PERMS } from '../../src/jam2/types';
import * as redisConfig from '../../src/config/redis';

function makeSong(id: string, duration = 200): Song {
  return {
    id,
    title: `Song ${id}`,
    artists: [{ id: `art_${id}`, name: `Artist ${id}` }],
    duration,
    imageUrl: null,
    year: '2024',
    language: 'en',
    hasLyrics: false,
    playCount: 10,
    label: 'Label',
    streamRef: `stream_${id}`,
    providerId: 'jiosaavn',
  };
}

function makeTestRoom(): Room {
  const hostUserId = 'host_1';
  const memberUserId = 'guest_1';
  const now = 1000000;

  return {
    code: 'TEST',
    version: 1,
    hostUserId,
    createdAt: now,
    members: new Map([
      [
        hostUserId,
        {
          userId: hostUserId,
          username: 'Host',
          joinedAt: now,
          online: true,
          lastSeenAt: now,
          perms: { queueAdd: true, playback: true, queueManage: true },
        },
      ],
      [
        memberUserId,
        {
          userId: memberUserId,
          username: 'Guest',
          joinedAt: now + 1000,
          online: true,
          lastSeenAt: now + 1000,
          perms: { ...DEFAULT_ROOM_PERMS, playback: false },
        },
      ],
    ]),
    defaults: { ...DEFAULT_ROOM_PERMS },
    queue: [
      {
        qid: 'q_next',
        song: makeSong('next_song', 180),
        addedByUserId: 'host_1',
        addedAt: now,
        recommended: false,
      },
    ],
    history: [],
    nowPlaying: {
      item: {
        qid: 'q_curr',
        song: makeSong('curr_song', 200), // 200 seconds = 200,000ms
        addedByUserId: 'host_1',
        addedAt: now,
        recommended: false,
      },
      status: 'playing',
      positionMs: 0,
      anchorServerTs: now,
      trackSeq: 1,
    },
  };
}

describe('Phase 1 Review Fixes (Part A)', () => {
  describe('A1: trackEnded validation', () => {
    it('rejects trackEnded from non-system actor at 1s of a 200s song (no advance)', () => {
      const room = makeTestRoom();
      const now = room.nowPlaying.anchorServerTs + 1000; // 1s played

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
        'guest_1',
        now,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.trackSeq).toBe(1);
        expect(res.room.nowPlaying.item?.song.id).toBe('curr_song');
        expect(res.room.queue.length).toBe(1);
      }
    });

    it('accepts trackEnded from non-system actor at 195s of a 200s song (advances once)', () => {
      const room = makeTestRoom();
      const now = room.nowPlaying.anchorServerTs + 195000; // 195s played (>= 200s - 6s)

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
        'guest_1',
        now,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.trackSeq).toBe(2);
        expect(res.room.nowPlaying.item?.song.id).toBe('next_song');
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('accepts trackEnded for unknown duration (0) only after 15s', () => {
      const room = makeTestRoom();
      room.nowPlaying.item!.song.duration = 0; // Unknown duration

      // At 5s: rejected
      const resEarly = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
        'guest_1',
        room.nowPlaying.anchorServerTs + 5000,
      );
      expect('room' in resEarly).toBe(true);
      if ('room' in resEarly) {
        expect(resEarly.room.nowPlaying.trackSeq).toBe(1);
      }

      // At 16s: accepted
      const resLate = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
        'guest_1',
        room.nowPlaying.anchorServerTs + 16000,
      );
      expect('room' in resLate).toBe(true);
      if ('room' in resLate) {
        expect(resLate.room.nowPlaying.trackSeq).toBe(2);
      }
    });

    it('exempts system actor from early trackEnded checks', () => {
      const room = makeTestRoom();
      // At 1s: system can advance (e.g. fallback timer or manual skip)
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
        'system',
        room.nowPlaying.anchorServerTs + 1000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.trackSeq).toBe(2);
      }
    });
  });

  describe('A2: Per-room serialization & Single-flight getRoom', () => {
    it('serializes 50 concurrent queueAdd commands without losing any', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');

      // 5 users sending 10 songs each concurrently
      const promises: Promise<any>[] = [];
      for (let userIdx = 0; userIdx < 5; userIdx++) {
        const userId = `user_${userIdx}`;
        // Register member
        await manager.joinRoom(room.code, userId, `User ${userIdx}`);
        for (let i = 0; i < 10; i++) {
          promises.push(
            manager.executeCommand(
              room.code,
              {
                code: room.code,
                type: 'queueAdd',
                song: makeSong(`s_${userIdx}_${i}`, 100),
              },
              userId,
            ),
          );
        }
      }

      await Promise.all(promises);

      const finalRoom = await store.getRoom(room.code);
      // First song auto-played, remaining 49 in queue (total 50 songs)
      expect(finalRoom?.nowPlaying.item).not.toBeNull();
      expect(finalRoom?.queue.length).toBe(49);
      manager.stop();
    });

    it('deduplicates concurrent getRoom rehydration calls (single-flight Redis GET)', async () => {
      const mockRedis: Record<string, string> = {
        'jam2:room:COLD': JSON.stringify({
          code: 'COLD',
          version: 1,
          hostUserId: 'u1',
          createdAt: 1000,
          members: [],
          defaults: {},
          queue: [],
          history: [],
          nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
        }),
      };

      const getSpy = vi.fn(async (key: string) => mockRedis[key] || null);
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: getSpy,
        set: vi.fn(),
        del: vi.fn(),
        scan: vi.fn(async () => ['0', []]),
      } as any);

      const store = new Jam2Store();
      // 20 concurrent getRoom calls
      const results = await Promise.all(
        Array.from({ length: 20 }, () => store.getRoom('COLD')),
      );

      expect(getSpy).toHaveBeenCalledTimes(1);
      expect(results[0]).not.toBeNull();
      // All callers get exact same Room instance reference
      for (let i = 1; i < 20; i++) {
        expect(results[i]).toBe(results[0]);
      }
    });

    it('does not poison the mutex if a command throws', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');

      // Command with invalid payload will fail, but shouldn't poison the lock
      try {
        await manager.executeCommand(
          room.code,
          { code: room.code, type: 'seek', positionMs: -100 } as any,
          'u_host',
        );
      } catch {}

      // Next command should succeed normally
      const res = await manager.executeCommand(
        room.code,
        { code: room.code, type: 'queueAdd', song: makeSong('ok_song') },
        'u_host',
      );
      expect('room' in res).toBe(true);
      manager.stop();
    });
  });

  describe('A3: Host invariants', () => {
    it('transfers host to offline member if host explicitly leaves and nobody else is online', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('host_1', 'Host');

      // Guest joined and went offline
      await manager.joinRoom(room.code, 'guest_offline', 'OfflineGuest');
      const r = await store.getRoom(room.code);
      r!.members.get('guest_offline')!.online = false;
      store.setRoom(r!);

      // Host leaves explicitly
      await manager.leaveRoom(room.code, 'host_1');

      const updated = await store.getRoom(room.code);
      expect(updated).not.toBeNull();
      expect(updated?.hostUserId).toBe('guest_offline');
      expect(updated?.members.has('host_1')).toBe(false);
      expect(updated?.members.has(updated.hostUserId)).toBe(true);
      manager.stop();
    });

    it('on rehydrate, sets lastSeenAt to now for all members to give host fresh grace', async () => {
      const rehydrateTime = 5000000;
      const store = new Jam2Store();
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: vi.fn(async () =>
          JSON.stringify({
            code: 'REHYDR',
            version: 1,
            hostUserId: 'u_host',
            createdAt: 1000,
            members: [
              {
                userId: 'u_host',
                username: 'Host',
                joinedAt: 1000,
                online: true,
                lastSeenAt: 2000, // Very old
                perms: { queueAdd: true, playback: true, queueManage: true },
              },
              {
                userId: 'u_guest',
                username: 'Guest',
                joinedAt: 2000,
                online: true,
                lastSeenAt: 2000, // Very old
                perms: { queueAdd: true, playback: false, queueManage: false },
              },
            ],
            defaults: {},
            queue: [],
            history: [],
            nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
          }),
        ),
      } as any);

      const room = await store.getRoom('REHYDR', rehydrateTime);
      expect(room).not.toBeNull();
      expect(room?.members.get('u_host')?.lastSeenAt).toBe(rehydrateTime);
      expect(room?.members.get('u_guest')?.lastSeenAt).toBe(rehydrateTime);
    });
  });

  describe('A4: Queue Invariants, playItem and prev', () => {
    it('playItem on recommended item plays it without putting skipped items into history', () => {
      const room = makeTestRoom();
      room.queue = [
        {
          qid: 'user_q1',
          song: makeSong('u1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        {
          qid: 'rec_q1',
          song: makeSong('r1'),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        },
      ];

      // Host plays recommended item 'rec_q1'
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'playItem', qid: 'rec_q1' },
        'host_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.item?.qid).toBe('rec_q1');
        // 'u1' should NOT be in history; it should remain in the upcoming queue!
        expect(res.room.queue.some((q) => q.qid === 'user_q1')).toBe(true);
        expect(res.room.history.length).toBe(1); // Only the previous nowPlaying ('curr_song') went to history
        expect(res.room.history[0].song.id).toBe('curr_song');
      }
    });

    it('prev on recommended nowPlaying inserts it at start of recommended section, not user section', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'rec_curr',
          song: makeSong('rec_now'),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        },
        status: 'playing',
        positionMs: 100, // <= 3000ms -> pops history
        anchorServerTs: 1000,
        trackSeq: 2,
      };

      room.history = [
        {
          qid: 'hist_prev',
          song: makeSong('previous'),
          addedByUserId: 'host_1',
          addedAt: 500,
          recommended: false,
        },
      ];

      room.queue = [
        {
          qid: 'user_upcoming',
          song: makeSong('u_upcoming'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
      ];

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'prev' },
        'host_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.item?.song.id).toBe('previous');
        // 'rec_now' must be in queue AFTER 'u_upcoming' (at start of recommendations)
        expect(res.room.queue[0].song.id).toBe('u_upcoming');
        expect(res.room.queue[1].song.id).toBe('rec_now');
        expect(res.room.queue[1].recommended).toBe(true);
      }
    });
  });

  describe('A5: Version safety on rehydrate', () => {
    it('bumps version by += 1000 on rehydration', async () => {
      const store = new Jam2Store();
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: vi.fn(async () =>
          JSON.stringify({
            code: 'VERS',
            version: 42,
            hostUserId: 'u1',
            createdAt: 1000,
            members: [],
            defaults: {},
            queue: [],
            history: [],
            nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
          }),
        ),
      } as any);

      const room = await store.getRoom('VERS');
      expect(room?.version).toBe(1042);
    });
  });

  describe('A7: Recommendations seeding guard', () => {
    it('stops adding recommendations when queue already has 10 recommended songs', () => {
      const room = makeTestRoom();
      room.queue = [];
      // Fill with 10 recommended songs
      for (let i = 0; i < 10; i++) {
        room.queue.push({
          qid: `rec_${i}`,
          song: makeSong(`r_${i}`),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        });
      }

      const res = applyCommand(
        room,
        {
          type: 'recsAdded',
          forTrackSeq: room.nowPlaying.trackSeq,
          songs: [makeSong('overflow_rec')],
        },
        'system',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        // Must still have 10, not 11
        const recCount = res.room.queue.filter((q) => q.recommended).length;
        expect(recCount).toBe(10);
      }
    });
  });
});

  describe('A1: Duplicate trackEnded', () => {
    it('20 duplicate trackEnded reports only advance once', () => {
      let room = makeTestRoom();
      const now = room.nowPlaying.anchorServerTs + 195000;
      let advances = 0;

      for (let i = 0; i < 20; i++) {
        const res = applyCommand(
          room,
          { code: 'TEST', type: 'trackEnded', trackSeq: 1 },
          'guest_1',
          now + i * 10,
        );
        if ('room' in res && res.room.nowPlaying.trackSeq !== room.nowPlaying.trackSeq) {
          advances++;
          room = res.room;
        }
      }

      expect(advances).toBe(1);
      expect(room.nowPlaying.trackSeq).toBe(2);
    });
  });

  describe('A2: Cold-start 20 concurrent joins', () => {
    it('cold-start 20 concurrent joins marks everyone online:true with single Redis GET', async () => {
      const mockRedis: Record<string, string> = {
        'jam2:room:COLD2': JSON.stringify({
          code: 'COLD2',
          version: 1,
          hostUserId: 'u_host',
          createdAt: 1000,
          members: [
            {
              userId: 'u_host',
              username: 'Host',
              joinedAt: 1000,
              online: false,
              lastSeenAt: 1000,
              perms: { queueAdd: true, playback: true, queueManage: true },
            },
          ],
          defaults: { queueAdd: true, playback: false, queueManage: false },
          queue: [],
          history: [],
          nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
        }),
      };

      const getSpy = vi.fn(async (key: string) => mockRedis[key] || null);
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: getSpy,
        set: vi.fn(),
        del: vi.fn(),
        scan: vi.fn(async () => ['0', []]),
      } as any);

      const store = new Jam2Store();
      const manager = new Jam2Manager(store);

      const joinPromises = Array.from({ length: 20 }, (_, i) =>
        manager.joinRoom('COLD2', `user_${i}`, `User ${i}`),
      );

      const results = await Promise.all(joinPromises);
      for (const res of results) {
        expect('room' in res).toBe(true);
      }

      expect(getSpy).toHaveBeenCalledTimes(1);

      const finalRoom = await store.getRoom('COLD2');
      for (let i = 0; i < 20; i++) {
        expect(finalRoom?.members.get(`user_${i}`)?.online).toBe(true);
      }
      manager.stop();
    });
  });

  describe('A3: Host Handover on Rehydrate & ensureHost', () => {
    it('first returning guest does not become host within 20s, but steals host after 25s', async () => {
      vi.useFakeTimers();
      const baseTime = 1000000;
      vi.setSystemTime(baseTime);

      const store = new Jam2Store();
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue({
        get: vi.fn(async () =>
          JSON.stringify({
            code: 'HANDO',
            version: 1,
            hostUserId: 'u_host',
            createdAt: 1000,
            members: [
              {
                userId: 'u_host',
                username: 'Host',
                joinedAt: 1000,
                online: false,
                lastSeenAt: 500,
                perms: { queueAdd: true, playback: true, queueManage: true },
              },
              {
                userId: 'u_guest',
                username: 'Guest',
                joinedAt: 2000,
                online: false,
                lastSeenAt: 500,
                perms: { queueAdd: true, playback: false, queueManage: false },
              },
            ],
            defaults: {},
            queue: [],
            history: [],
            nowPlaying: { item: null, status: 'idle', positionMs: 0, anchorServerTs: 1000, trackSeq: 0 },
          }),
        ),
      } as any);

      const manager = new Jam2Manager(store);

      // Rehydrate happens at baseTime; both members get lastSeenAt = baseTime
      await store.getRoom('HANDO', baseTime);

      // Guest returns at t = baseTime + 5s (within 20s grace)
      vi.setSystemTime(baseTime + 5000);
      await manager.joinRoom('HANDO', 'u_guest', 'Guest');

      let room = await store.getRoom('HANDO');
      expect(room?.hostUserId).toBe('u_host'); // Host not stolen yet

      // Advance to t = baseTime + 25s (> 20s host grace)
      vi.setSystemTime(baseTime + 25000);
      await manager.ensureHost('HANDO', baseTime + 25000);

      room = await store.getRoom('HANDO');
      expect(room?.hostUserId).toBe('u_guest'); // Host passed to online guest

      manager.stop();
      vi.useRealTimers();
    });
  });

  describe('A4: Randomized 2,000 Commands Invariant Test', () => {
    it('preserves all invariants across 2,000 random commands', () => {
      let seed = 987654321;
      const rng = () => {
        seed = (seed * 1664525 + 1013904223) % 4294967296;
        return seed / 4294967296;
      };

      let room = makeTestRoom();
      room.members.set('guest_perms', {
        userId: 'guest_perms',
        username: 'GuestPerms',
        joinedAt: 2000,
        online: true,
        lastSeenAt: 2000,
        perms: { queueAdd: true, playback: true, queueManage: true },
      });
      room.members.set('guest_noperms', {
        userId: 'guest_noperms',
        username: 'GuestNoPerms',
        joinedAt: 3000,
        online: true,
        lastSeenAt: 3000,
        perms: { queueAdd: false, playback: false, queueManage: false },
      });

      const actors = ['host_1', 'guest_perms', 'guest_noperms', 'stranger_99'];
      let now = 1000000;

      for (let step = 0; step < 2000; step++) {
        now += Math.floor(rng() * 1000);
        const actor = actors[Math.floor(rng() * actors.length)];
        const op = Math.floor(rng() * 14);
        const prevVersion = room.version;

        let cmd: any;
        switch (op) {
          case 0:
            cmd = { code: 'TEST', type: 'play' };
            break;
          case 1:
            cmd = { code: 'TEST', type: 'pause' };
            break;
          case 2:
            cmd = { code: 'TEST', type: 'seek', positionMs: Math.floor(rng() * 300000) };
            break;
          case 3:
            cmd = { code: 'TEST', type: 'next' };
            break;
          case 4:
            cmd = { code: 'TEST', type: 'prev' };
            break;
          case 5:
            cmd = {
              code: 'TEST',
              type: 'playItem',
              qid: room.queue[Math.floor(rng() * Math.max(1, room.queue.length))]?.qid ?? 'fake_qid',
            };
            break;
          case 6:
            cmd = { code: 'TEST', type: 'trackEnded', trackSeq: room.nowPlaying.trackSeq };
            break;
          case 7:
            cmd = {
              code: 'TEST',
              type: 'queueAdd',
              song: makeSong(`rnd_${step}`, Math.floor(rng() * 300)),
              playNext: rng() > 0.5,
            };
            break;
          case 8:
            cmd = {
              code: 'TEST',
              type: 'promote',
              qid: room.queue.find((q) => q.recommended)?.qid ?? 'fake_qid',
            };
            break;
          case 9:
            cmd = {
              code: 'TEST',
              type: 'queueRemove',
              qid: room.queue[Math.floor(rng() * Math.max(1, room.queue.length))]?.qid ?? 'fake_qid',
            };
            break;
          case 10:
            cmd = {
              code: 'TEST',
              type: 'queueMove',
              qid: room.queue[0]?.qid ?? 'fake_qid',
              toIndex: Math.floor(rng() * 10),
            };
            break;
          case 11:
            cmd = {
              type: 'recsAdded',
              forTrackSeq: room.nowPlaying.trackSeq,
              songs: [makeSong(`rec_s_${step}`, 150)],
            };
            break;
          case 12:
            cmd = {
              code: 'TEST',
              type: 'setMemberPerms',
              userId: 'guest_noperms',
              perms: { playback: rng() > 0.5 },
            };
            break;
          case 13:
          default:
            cmd = {
              type: 'memberOnline',
              userId: `user_new_${step % 10}`,
              username: `UserNew_${step % 10}`,
            };
            break;
        }

        const res = applyCommand(room, cmd, actor, now);
        if ('room' in res) {
          if (res.room !== room) {
            // Invariant 1: version strictly increasing on change
            expect(res.room.version).toBeGreaterThan(prevVersion);
            room = res.room;
          } else {
            // Invariant 1b: rejected / no-op -> room identical, version unchanged
            expect(res.room).toBe(room);
            expect(room.version).toBe(prevVersion);
          }
        }

        // Invariant 2: Unique qids in queue
        const qids = room.queue.map((q) => q.qid);
        expect(new Set(qids).size).toBe(qids.length);

        // Invariant 3: User items before recommended items
        const firstRecIdx = room.queue.findIndex((q) => q.recommended);
        if (firstRecIdx !== -1) {
          for (let i = firstRecIdx; i < room.queue.length; i++) {
            expect(room.queue[i].recommended).toBe(true);
          }
        }

        // Invariant 4: Queue length <= 50
        expect(room.queue.length).toBeLessThanOrEqual(50);

        // Invariant 5: History length <= 20
        expect(room.history.length).toBeLessThanOrEqual(20);

        // Invariant 6: hostUserId in members if members > 0
        if (room.members.size > 0) {
          expect(room.members.has(room.hostUserId)).toBe(true);
        }
      }
    });
  });

  describe('A6: Already-online join & lighter heartbeat', () => {
    it('already-online join does not bump version or broadcast', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');

      await manager.joinRoom(room.code, 'u_guest', 'Guest');
      const roomAfterFirstJoin = await store.getRoom(room.code);
      const versionBefore = roomAfterFirstJoin!.version;

      // Guest joins again while already online
      const res = await manager.joinRoom(room.code, 'u_guest', 'Guest');
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.version).toBe(versionBefore);
        expect(res.events.length).toBe(0);
      }
      manager.stop();
    });
  });

  describe('A7: Seeding guard against rapid commands', () => {
    it('30 commands in a row cause at most 1 recommendations fetch per trackSeq', async () => {
      const store = new Jam2Store();
      const mockFetch = vi.fn().mockResolvedValue([makeSong('rec_1'), makeSong('rec_2')]);
      const manager = new Jam2Manager(store, mockFetch);

      const room = await manager.createRoom('u_host', 'Host');
      await manager.executeCommand(
        room.code,
        { code: room.code, type: 'queueAdd', song: makeSong('now_song', 200) },
        'u_host',
      );

      // Send 30 commands rapidly
      for (let i = 0; i < 30; i++) {
        await manager.executeCommand(
          room.code,
          { code: room.code, type: 'seek', positionMs: i * 1000 },
          'u_host',
        );
      }

      await new Promise((r) => setTimeout(r, 50));
      expect(mockFetch).toHaveBeenCalledTimes(1);
      manager.stop();
    });
  });

  describe('A8: Kick ban for 10 minutes', () => {
    it('rejects kicked user from rejoining for 10 minutes with FORBIDDEN', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');

      await manager.joinRoom(room.code, 'bad_user', 'BadUser');

      // Host kicks bad_user
      await manager.executeCommand(
        room.code,
        { code: room.code, type: 'kick', userId: 'bad_user' },
        'u_host',
      );

      // bad_user tries to rejoin immediately
      const rejoinRes = await manager.joinRoom(room.code, 'bad_user', 'BadUser');
      expect('error' in rejoinRes).toBe(true);
      if ('error' in rejoinRes) {
        expect(rejoinRes.error.code).toBe('FORBIDDEN');
        expect(rejoinRes.error.message).toContain('removed from this Jam');
      }
      manager.stop();
    });
  });

  describe('Item 11: KeyedMutex cleanup & Prune loop re-fetch', () => {
    it('KeyedMutex retains chain map entry while concurrent callers are queued and deletes only when idle', async () => {
      const mutex = new KeyedMutex();
      const key = 'ROOM_TEST';

      let task1Entered = false;
      let task1Release: () => void = () => {};
      const task1Blocker = new Promise<void>((resolve) => {
        task1Release = resolve;
      });

      let task2Entered = false;

      // Start Task 1
      const p1 = mutex.runExclusive(key, async () => {
        task1Entered = true;
        await task1Blocker;
        return 'res1';
      });

      // Wait until Task 1 is inside runExclusive
      while (!task1Entered) {
        await new Promise((r) => setTimeout(r, 5));
      }

      // Start Task 2 while Task 1 is still holding the lock
      const p2 = mutex.runExclusive(key, async () => {
        task2Entered = true;
        return 'res2';
      });

      // Task 1 now finishes
      task1Release();
      const r1 = await p1;
      expect(r1).toBe('res1');

      // Crucial assertion: when Task 1 finished, Task 2 was already chained.
      // The chains map must NOT have been deleted when Task 1 completed!
      expect((mutex as any).chains.has(key)).toBe(true);

      const r2 = await p2;
      expect(r2).toBe('res2');
      expect(task2Entered).toBe(true);

      // Now that all queued callers are done, the chains map should be cleaned up!
      expect((mutex as any).chains.has(key)).toBe(false);
    });

    it('pruneActiveRooms re-fetches room inside lock and skips if room was destroyed', async () => {
      const store = new Jam2Store();
      const manager = new Jam2Manager(store);
      const room = await manager.createRoom('u_host', 'Host');

      // Spy on store.getRoom to verify it gets called inside prune
      const getRoomSpy = vi.spyOn(store, 'getRoom');

      // Destroy the room right before prune acquires lock or inside lock
      await manager.endRoom(room.code, 'u_host');

      // Run prune loop
      await manager.pruneActiveRooms(Date.now() + 60 * 60 * 1000);

      // getRoom should have been called for room.code
      expect(getRoomSpy).toHaveBeenCalledWith(room.code);

      manager.stop();
    });
  });
