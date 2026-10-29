import { describe, it, expect } from 'vitest';
import { applyCommand } from '../../src/jam2/reducer';
import {
  Room,
  Song,
  QueueItem,
  DEFAULT_ROOM_PERMS,
  getExpectedPosition,
} from '../../src/jam2/types';

function makeTestSong(id: string, title = `Song ${id}`, duration = 200): Song {
  return {
    id,
    title,
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
          perms: { ...DEFAULT_ROOM_PERMS }, // queueAdd: true, playback: false, queueManage: false
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
}

describe('Jam2 Reducer', () => {
  describe('Permissions Enforcement', () => {
    it('allows host to execute playback commands', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 1000,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      const res = applyCommand(room, { code: 'TEST', type: 'pause' }, 'host_1', 2000);
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.status).toBe('paused');
      }
    });

    it('rejects guest without playback permission from pausing or seeking', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 1000,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      const res = applyCommand(room, { code: 'TEST', type: 'pause' }, 'guest_1', 2000);
      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('FORBIDDEN');
      }
    });

    it('allows guest with playback permission to pause', () => {
      const room = makeTestRoom();
      room.members.get('guest_1')!.perms.playback = true;
      room.nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 1000,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      const res = applyCommand(room, { code: 'TEST', type: 'pause' }, 'guest_1', 2000);
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.status).toBe('paused');
      }
    });

    it('allows guest with queueAdd to add songs', () => {
      const room = makeTestRoom();
      const song = makeTestSong('s1');
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song },
        'guest_1',
        2000,
      );
      expect('room' in res).toBe(true);
    });

    it('rejects guest without queueAdd from adding songs', () => {
      const room = makeTestRoom();
      room.members.get('guest_1')!.perms.queueAdd = false;
      const song = makeTestSong('s1');
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song },
        'guest_1',
        2000,
      );
      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('FORBIDDEN');
      }
    });

    it('allows guest to remove their own song without queueManage', () => {
      const room = makeTestRoom();
      room.queue.push({
        qid: 'guest_qid_1',
        song: makeTestSong('s1'),
        addedByUserId: 'guest_1',
        addedAt: 1000,
        recommended: false,
      });

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueRemove', qid: 'guest_qid_1' },
        'guest_1',
        2000,
      );
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('rejects guest from removing someone else song without queueManage', () => {
      const room = makeTestRoom();
      room.queue.push({
        qid: 'host_qid_1',
        song: makeTestSong('s1'),
        addedByUserId: 'host_1',
        addedAt: 1000,
        recommended: false,
      });

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueRemove', qid: 'host_qid_1' },
        'guest_1',
        2000,
      );
      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('FORBIDDEN');
      }
    });

    it('allows guest with queueManage to remove someone else song', () => {
      const room = makeTestRoom();
      room.members.get('guest_1')!.perms.queueManage = true;
      room.queue.push({
        qid: 'host_qid_1',
        song: makeTestSong('s1'),
        addedByUserId: 'host_1',
        addedAt: 1000,
        recommended: false,
      });

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueRemove', qid: 'host_qid_1' },
        'guest_1',
        2000,
      );
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('allows guest with queueAdd to dismiss a recommendation', () => {
      const room = makeTestRoom();
      room.queue.push({
        qid: 'rec_qid_1',
        song: makeTestSong('s_rec'),
        addedByUserId: null,
        addedAt: 1000,
        recommended: true,
      });

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueRemove', qid: 'rec_qid_1' },
        'guest_1',
        2000,
      );
      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('rejects non-host from administrative actions (kick, perms, transfer)', () => {
      const room = makeTestRoom();
      const resKick = applyCommand(
        room,
        { code: 'TEST', type: 'kick', userId: 'host_1' },
        'guest_1',
        2000,
      );
      expect('error' in resKick).toBe(true);

      const resPerms = applyCommand(
        room,
        {
          code: 'TEST',
          type: 'setMemberPerms',
          userId: 'guest_1',
          perms: { playback: true },
        },
        'guest_1',
        2000,
      );
      expect('error' in resPerms).toBe(true);
    });

    it('rejects actions from non-members with NOT_MEMBER', () => {
      const room = makeTestRoom();
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'play' },
        'stranger_99',
        2000,
      );
      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('NOT_MEMBER');
      }
    });
  });

  describe('Queue Operations & Invariants', () => {
    it('auto-starts playback on idle room when first song added', () => {
      const room = makeTestRoom();
      expect(room.nowPlaying.status).toBe('idle');
      expect(room.nowPlaying.item).toBeNull();

      const song = makeTestSong('s1', 'First Song', 180);
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song },
        'host_1',
        5000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.status).toBe('playing');
        expect(res.room.nowPlaying.item?.song.id).toBe('s1');
        expect(res.room.nowPlaying.trackSeq).toBe(1);
        expect(res.room.nowPlaying.anchorServerTs).toBe(5000);
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('appends user songs after other user songs but BEFORE recommended songs', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      // Add a user song and a recommended song
      room.queue = [
        {
          qid: 'user_q1',
          song: makeTestSong('u1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        {
          qid: 'rec_q1',
          song: makeTestSong('r1'),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        },
      ];

      const newSong = makeTestSong('u2');
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: newSong },
        'guest_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        // Expected order: u1, u2, r1
        expect(res.room.queue.map((q) => q.song.id)).toEqual(['u1', 'u2', 'r1']);
      }
    });

    it('supports playNext by inserting at head of queue', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };
      room.queue = [
        {
          qid: 'user_q1',
          song: makeTestSong('u1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
      ];

      const urgentSong = makeTestSong('urgent');
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: urgentSong, playNext: true },
        'host_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue[0].song.id).toBe('urgent');
        expect(res.room.queue[1].song.id).toBe('u1');
      }
    });

    it('prevents adding duplicate songs already playing or queued, moves to front if playNext', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('s1', 'Kesariya'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      // 1. Adding currently playing song is skipped as duplicate
      const dupPlaying = makeTestSong('s1', 'Kesariya');
      const res1 = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: dupPlaying },
        'host_1',
        2000,
      );
      expect('room' in res1).toBe(true);
      if ('room' in res1) {
        expect(res1.added).toBe(0);
        expect(res1.skippedDuplicate).toBe(1);
        expect(res1.room.queue.length).toBe(0);
      }

      // Add a song to queue
      const song2 = makeTestSong('s2', 'Channa Mereya');
      const res2 = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: song2 },
        'host_1',
        3000,
      );
      expect('room' in res2).toBe(true);
      if ('room' in res2) {
        expect(res2.room.queue.length).toBe(1);
        expect(res2.room.queue[0].song.id).toBe('s2');

        // 2. Adding the queued song again without playNext is skipped as duplicate
        const res3 = applyCommand(
          res2.room,
          { code: 'TEST', type: 'queueAdd', song: song2 },
          'guest_1',
          4000,
        );
        expect('room' in res3).toBe(true);
        if ('room' in res3) {
          expect(res3.added).toBe(0);
          expect(res3.skippedDuplicate).toBe(1);
          expect(res3.room.queue.length).toBe(1);
        }

        // Add a 3rd song
        const song3 = makeTestSong('s3', 'Tum Hi Ho');
        const res4 = applyCommand(
          res2.room,
          { code: 'TEST', type: 'queueAdd', song: song3 },
          'guest_1',
          4500,
        );
        expect('room' in res4).toBe(true);
        if ('room' in res4) {
          expect(res4.room.queue.map((q) => q.song.id)).toEqual(['s2', 's3']);

          // 3. Adding song3 with playNext moves it to the front
          const res5 = applyCommand(
            res4.room,
            { code: 'TEST', type: 'queueAdd', song: song3, playNext: true },
            'guest_1',
            5000,
          );
          expect('room' in res5).toBe(true);
          if ('room' in res5) {
            expect(res5.room.queue.map((q) => q.song.id)).toEqual(['s3', 's2']);
          }
        }
      }
    });

    it('replaces recommendation if user explicitly queues the same song', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };
      room.queue = [
        {
          qid: 'rec_s1',
          song: makeTestSong('s1', 'Song s1'),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        },
      ];

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: makeTestSong('s1', 'Song s1') },
        'guest_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(1);
        expect(res.room.queue[0].recommended).toBe(false);
        expect(res.room.queue[0].addedByUserId).toBe('guest_1');
      }
    });

    it('preserves recommended songs separately and does not evict them when adding user songs', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      // 48 user songs + 2 recommended songs = 50 total
      for (let i = 0; i < 48; i++) {
        room.queue.push({
          qid: `u_${i}`,
          song: makeTestSong(`u_${i}`),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        });
      }
      room.queue.push({
        qid: 'r_1',
        song: makeTestSong('r_1'),
        addedByUserId: null,
        addedAt: 1000,
        recommended: true,
      });
      room.queue.push({
        qid: 'r_2',
        song: makeTestSong('r_2'),
        addedByUserId: null,
        addedAt: 1000,
        recommended: true,
      });

      expect(room.queue.length).toBe(50);

      // Add 49th user song -> both recommendations are preserved, total becomes 51
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: makeTestSong('new_user_song') },
        'guest_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(51);
        expect(res.room.queue.some((q) => q.song.id === 'r_1')).toBe(true);
        expect(res.room.queue.some((q) => q.song.id === 'r_2')).toBe(true);
        expect(res.room.queue.some((q) => q.song.id === 'new_user_song')).toBe(true);
      }
    });

    it('rejects queueAdd with QUEUE_FULL when 50 user songs already exist', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      for (let i = 0; i < 50; i++) {
        room.queue.push({
          qid: `u_${i}`,
          song: makeTestSong(`u_${i}`),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        });
      }

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'queueAdd', song: makeTestSong('overflow_song') },
        'host_1',
        2000,
      );

      expect('error' in res).toBe(true);
      if ('error' in res) {
        expect(res.error.code).toBe('QUEUE_FULL');
      }
    });

    it('promotes a recommended song to user section and clears recommended flag', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'now_q',
          song: makeTestSong('now_song'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };
      room.queue = [
        {
          qid: 'rec_1',
          song: makeTestSong('r1'),
          addedByUserId: null,
          addedAt: 1000,
          recommended: true,
        },
      ];

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'promote', qid: 'rec_1' },
        'guest_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        const item = res.room.queue[0];
        expect(item.recommended).toBe(false);
        expect(item.addedByUserId).toBe('guest_1');
      }
    });
  });

  describe('Playback Flow & Idempotency', () => {
    it('trackEnded advances queue and appends previous track to history (capped at 20)', () => {
      const room = makeTestRoom();
      const current = makeTestSong('current');
      const nextSong = makeTestSong('next');

      room.nowPlaying = {
        item: {
          qid: 'q_curr',
          song: current,
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 200000,
        anchorServerTs: 1000,
        trackSeq: 5,
      };

      room.queue = [
        {
          qid: 'q_next',
          song: nextSong,
          addedByUserId: 'guest_1',
          addedAt: 1500,
          recommended: false,
        },
      ];

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 5 },
        'guest_1',
        3000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.item?.song.id).toBe('next');
        expect(res.room.nowPlaying.trackSeq).toBe(6);
        expect(res.room.nowPlaying.positionMs).toBe(0);
        expect(res.room.nowPlaying.anchorServerTs).toBe(3000);
        expect(res.room.history.length).toBe(1);
        expect(res.room.history[0].song.id).toBe('current');
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('trackEnded ignores stale trackSeq (idempotent)', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q_curr',
          song: makeTestSong('current'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 10000,
        anchorServerTs: 1000,
        trackSeq: 10,
      };

      // Stale trackSeq 9
      const res = applyCommand(
        room,
        { code: 'TEST', type: 'trackEnded', trackSeq: 9 },
        'guest_1',
        3000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.trackSeq).toBe(10);
        expect(res.room.history.length).toBe(0);
      }
    });

    it('prev restarts track if position > 3000ms', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q_curr',
          song: makeTestSong('current'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 10000,
        anchorServerTs: 1000,
        trackSeq: 2,
      };

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'prev' },
        'host_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.positionMs).toBe(0);
        expect(res.room.nowPlaying.trackSeq).toBe(2);
      }
    });

    it('prev pops previous track from history if position <= 3000ms', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q_curr',
          song: makeTestSong('current'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 1000,
        anchorServerTs: 1000,
        trackSeq: 2,
      };

      room.history.push({
        qid: 'q_prev',
        song: makeTestSong('previous_track'),
        addedByUserId: 'host_1',
        addedAt: 500,
        recommended: false,
      });

      const res = applyCommand(
        room,
        { code: 'TEST', type: 'prev' },
        'host_1',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.nowPlaying.item?.song.id).toBe('previous_track');
        expect(res.room.nowPlaying.trackSeq).toBe(3);
        expect(res.room.history.length).toBe(0);
        // Current track placed back at front of queue
        expect(res.room.queue[0].song.id).toBe('current');
      }
    });
  });

  describe('Position Math & Clamping', () => {
    it('calculates expected position when playing and clamps to duration', () => {
      const nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1', 'Title', 100), // 100s = 100,000ms
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing' as const,
        positionMs: 10000,
        anchorServerTs: 20000,
        trackSeq: 1,
      };

      // Elapsed: 5,000ms -> expected: 15,000ms
      expect(getExpectedPosition(nowPlaying, 25000)).toBe(15000);

      // Elapsed: 200,000ms -> clamped to duration 100,000ms
      expect(getExpectedPosition(nowPlaying, 220000)).toBe(100000);
    });

    it('freezes position when paused', () => {
      const nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1', 'Title', 100),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'paused' as const,
        positionMs: 42000,
        anchorServerTs: 20000,
        trackSeq: 1,
      };

      expect(getExpectedPosition(nowPlaying, 999999)).toBe(42000);
    });
  });

  describe('Internal Commands', () => {
    it('recsAdded appends deduplicated recommendations', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 1,
      };

      const recCandidates = [
        makeTestSong('s1'), // Duplicate of nowPlaying -> skipped
        makeTestSong('rec_1'),
        makeTestSong('rec_2'),
      ];

      const res = applyCommand(
        room,
        { type: 'recsAdded', forTrackSeq: 1, songs: recCandidates },
        'system',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(2);
        expect(res.room.queue[0].song.id).toBe('rec_1');
        expect(res.room.queue[0].recommended).toBe(true);
        expect(res.room.queue[1].song.id).toBe('rec_2');
      }
    });

    it('recsAdded ignores candidates for stale trackSeq', () => {
      const room = makeTestRoom();
      room.nowPlaying = {
        item: {
          qid: 'q1',
          song: makeTestSong('s1'),
          addedByUserId: 'host_1',
          addedAt: 1000,
          recommended: false,
        },
        status: 'playing',
        positionMs: 0,
        anchorServerTs: 1000,
        trackSeq: 2,
      };

      const res = applyCommand(
        room,
        { type: 'recsAdded', forTrackSeq: 1, songs: [makeTestSong('r')] },
        'system',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.queue.length).toBe(0);
      }
    });

    it('memberOnline sets online flag and creates new member with default perms if new', () => {
      const room = makeTestRoom();
      const res = applyCommand(
        room,
        {
          type: 'memberOnline',
          userId: 'new_guest',
          username: 'Newbie',
          avatarUrl: 'https://example.com/avatar.png',
        },
        'system',
        2000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        const m = res.room.members.get('new_guest');
        expect(m).toBeDefined();
        expect(m?.username).toBe('Newbie');
        expect(m?.online).toBe(true);
        expect(m?.perms.queueAdd).toBe(true);
        expect(m?.perms.playback).toBe(false);
      }
    });

    it('memberOffline sets online flag to false and updates lastSeenAt', () => {
      const room = makeTestRoom();
      const res = applyCommand(
        room,
        { type: 'memberOffline', userId: 'guest_1' },
        'system',
        5000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        const m = res.room.members.get('guest_1');
        expect(m?.online).toBe(false);
        expect(m?.lastSeenAt).toBe(5000);
      }
    });

    it('memberLeave removes member from room', () => {
      const room = makeTestRoom();
      const res = applyCommand(
        room,
        { type: 'memberLeave', userId: 'guest_1' },
        'system',
        5000,
      );

      expect('room' in res).toBe(true);
      if ('room' in res) {
        expect(res.room.members.has('guest_1')).toBe(false);
      }
    });
  });
});

