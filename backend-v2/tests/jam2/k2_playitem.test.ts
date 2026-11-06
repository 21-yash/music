import { describe, it, expect } from 'vitest';
import { applyCommand } from '../../src/jam2/reducer';
import { Room, Song, QueueItem } from '../../src/jam2/types';

function makeSong(id: string): Song {
  return {
    id,
    title: `Song ${id}`,
    artists: [{ id: 'a1', name: 'Artist' }],
    duration: 180,
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

function makeQueueItem(id: string, qid: string, recommended: boolean = false): QueueItem {
  return {
    qid,
    song: makeSong(id),
    addedByUserId: recommended ? null : 'user_1',
    addedAt: 1000,
    recommended,
  };
}

function makeTestRoom(): Room {
  return {
    code: 'TEST',
    version: 1,
    hostUserId: 'host_1',
    createdAt: 1000,
    members: new Map([
      [
        'host_1',
        {
          userId: 'host_1',
          username: 'Host',
          joinedAt: 1000,
          online: true,
          lastSeenAt: 1000,
          perms: { queueAdd: true, playback: true, queueManage: true },
        },
      ],
    ]),
    defaults: { queueAdd: true, playback: true, queueManage: true },
    queue: [],
    history: [],
    nowPlaying: {
      item: makeQueueItem('init_np', 'qid_init'),
      status: 'playing',
      positionMs: 5000,
      anchorServerTs: 1000,
      trackSeq: 1,
    },
  };
}

describe('Task K2: playItem keeps preceding queue items for all items', () => {
  it('queue [A, B, C, D, E, F], playItem(D) -> nowPlaying is D, queue is [A, B, C, E, F], history gains only previous nowPlaying', () => {
    const room = makeTestRoom();
    room.queue = [
      makeQueueItem('A', 'qid_A'),
      makeQueueItem('B', 'qid_B'),
      makeQueueItem('C', 'qid_C'),
      makeQueueItem('D', 'qid_D'),
      makeQueueItem('E', 'qid_E'),
      makeQueueItem('F', 'qid_F'),
    ];

    const res = applyCommand(
      room,
      { code: 'TEST', type: 'playItem', qid: 'qid_D' },
      'host_1',
      2000,
    );

    expect('room' in res).toBe(true);
    if ('room' in res) {
      expect(res.room.nowPlaying.item?.qid).toBe('qid_D');
      expect(res.room.nowPlaying.item?.song.id).toBe('D');

      // Preceding items A, B, C MUST remain in the queue!
      const remainingQids = res.room.queue.map((q) => q.qid);
      expect(remainingQids).toEqual(['qid_A', 'qid_B', 'qid_C', 'qid_E', 'qid_F']);

      // History gains ONLY the previous nowPlaying ('init_np'), NOT skipped items A, B, C
      expect(res.room.history.length).toBe(1);
      expect(res.room.history[0].qid).toBe('qid_init');
    }
  });

  it('playItem on recommended item removes only that item, queue order preserved', () => {
    const room = makeTestRoom();
    room.queue = [
      makeQueueItem('A', 'qid_A', false),
      makeQueueItem('B', 'qid_B', false),
      makeQueueItem('R1', 'qid_R1', true),
      makeQueueItem('R2', 'qid_R2', true),
    ];

    const res = applyCommand(
      room,
      { code: 'TEST', type: 'playItem', qid: 'qid_R1' },
      'host_1',
      2000,
    );

    expect('room' in res).toBe(true);
    if ('room' in res) {
      expect(res.room.nowPlaying.item?.qid).toBe('qid_R1');
      const remainingQids = res.room.queue.map((q) => q.qid);
      expect(remainingQids).toEqual(['qid_A', 'qid_B', 'qid_R2']);
      expect(res.room.history.length).toBe(1);
      expect(res.room.history[0].qid).toBe('qid_init');
    }
  });

  it('playItem on first item in queue: nowPlaying becomes first item, queue becomes [B, C, ...]', () => {
    const room = makeTestRoom();
    room.queue = [
      makeQueueItem('A', 'qid_A'),
      makeQueueItem('B', 'qid_B'),
      makeQueueItem('C', 'qid_C'),
    ];

    const res = applyCommand(
      room,
      { code: 'TEST', type: 'playItem', qid: 'qid_A' },
      'host_1',
      2000,
    );

    expect('room' in res).toBe(true);
    if ('room' in res) {
      expect(res.room.nowPlaying.item?.qid).toBe('qid_A');
      const remainingQids = res.room.queue.map((q) => q.qid);
      expect(remainingQids).toEqual(['qid_B', 'qid_C']);
      expect(res.room.history.length).toBe(1);
      expect(res.room.history[0].qid).toBe('qid_init');
    }
  });

  it('playItem on unknown qid returns error NOT_FOUND, queue unchanged', () => {
    const room = makeTestRoom();
    room.queue = [
      makeQueueItem('A', 'qid_A'),
      makeQueueItem('B', 'qid_B'),
    ];

    const res = applyCommand(
      room,
      { code: 'TEST', type: 'playItem', qid: 'unknown_qid' },
      'host_1',
      2000,
    );

    expect('error' in res).toBe(true);
    if ('error' in res) {
      expect(res.error.code).toBe('NOT_FOUND');
    }
  });
});
