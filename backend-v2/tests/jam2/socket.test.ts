import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';
import http from 'http';
import { io as ioClient, Socket as ClientSocket } from 'socket.io-client';
import { createApp } from '../../src/app';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';
import { initializeSocketIO } from '../../src/websocket';
import { Jam2Ack, RoomSnapshot, Song } from '../../src/jam2/types';
import { jam2Manager } from '../../src/jam2/manager';
import { setCustomSongResolver } from '../../src/jam2/songResolver';
import * as songResolverModule from '../../src/jam2/songResolver';

function makeTestSong(id: string): Song {
  return {
    id,
    title: `Song ${id}`,
    artists: [{ id: 'a1', name: 'Artist' }],
    duration: 200,
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

describe('Jam 2.0 WebSocket Integration', () => {
  let server: http.Server;
  let port: number;

  const HOST_USER_ID = 'host-user-id-123456789012';
  const GUEST_USER_ID = 'guest-user-id-123456789012';

  const hostToken = generateAccessToken({ userId: HOST_USER_ID, username: 'host_alice' });
  const guestToken = generateAccessToken({ userId: GUEST_USER_ID, username: 'guest_bob' });

  let hostSocket: ClientSocket;
  let guestSocket: ClientSocket;

  beforeAll(async () => {
    const app = createApp();
    server = http.createServer(app);
    initializeSocketIO(server);

    await new Promise<void>((resolve) => {
      server.listen(0, () => {
        const addr = server.address() as any;
        port = addr.port;
        resolve();
      });
    });

    hostSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: env.APP_API_KEY, token: hostToken },
      transports: ['websocket'],
      reconnection: false,
    });

    guestSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: env.APP_API_KEY, token: guestToken },
      transports: ['websocket'],
      reconnection: false,
    });

    await Promise.all([
      new Promise<void>((res) => hostSocket.on('connect', () => res())),
      new Promise<void>((res) => guestSocket.on('connect', () => res())),
    ]);

    setCustomSongResolver(async (id, providerId) => {
      return makeTestSong(id);
    });
  });

  beforeEach(() => {
    jam2Manager.clearCreateRateLimits();
  });

  afterAll(async () => {
    setCustomSongResolver(null);
    jam2Manager.stop();
    if (hostSocket && hostSocket.connected) hostSocket.disconnect();
    if (guestSocket && guestSocket.connected) guestSocket.disconnect();
    await new Promise<void>((res) => server.close(() => res()));
  });

  it('rejects protocol version mismatch in jam2:create', async () => {
    const ack: Jam2Ack = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 1 }, res);
    });

    expect(ack.ok).toBe(false);
    if (!ack.ok) {
      expect(ack.code).toBe('PROTOCOL');
      expect(ack.message).toContain('Unsupported protocol version');
    }
  });

  it('creates a room with protocol 2 and returns snapshot', async () => {
    const ack: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });

    expect(ack.ok).toBe(true);
    if (ack.ok) {
      expect(ack.room.code.length).toBe(4);
      expect(ack.room.hostUserId).toBe(HOST_USER_ID);
      expect(ack.room.members.length).toBe(1);
      expect(ack.room.members[0].username).toBe('host_alice');
    }
  });

  it('guest joins room, triggers member_joined event to host, and receives full snapshot', async () => {
    // 1. Host creates room
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(createAck.ok).toBe(true);
    if (!createAck.ok) return;

    const code = createAck.room.code;

    // Listen for member_joined on host socket
    const hostEventPromise = new Promise<any>((res) => {
      hostSocket.once('jam2:event', (ev) => res(ev));
    });

    // 2. Guest joins
    const joinAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      guestSocket.emit('jam2:join', { protocol: 2, code }, res);
    });

    expect(joinAck.ok).toBe(true);
    if (joinAck.ok) {
      expect(joinAck.room.members.length).toBe(2);
      expect(joinAck.room.members.some((m) => m.userId === GUEST_USER_ID)).toBe(true);
    }

    const hostEvent = await hostEventPromise;
    expect(hostEvent.kind).toBe('member_joined');
    expect(hostEvent.userId).toBe(GUEST_USER_ID);
  });

  it('answers jam2:time with serverNow', async () => {
    const ack: Jam2Ack<{ serverNow: number }> = await new Promise((res) => {
      hostSocket.emit('jam2:time', {}, res);
    });

    expect(ack.ok).toBe(true);
    if (ack.ok) {
      expect(typeof ack.serverNow).toBe('number');
      expect(Math.abs(Date.now() - ack.serverNow)).toBeLessThan(1000);
    }
  });

  it('rejects forbidden guest commands', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    if (!createAck.ok) return;
    const code = createAck.room.code;

    await new Promise((res) => guestSocket.emit('jam2:join', { protocol: 2, code }, res));

    // Guest tries to kick host
    const kickAck: Jam2Ack = await new Promise((res) => {
      guestSocket.emit(
        'jam2:cmd',
        { code, type: 'kick', userId: HOST_USER_ID },
        res,
      );
    });
    expect(kickAck.ok).toBe(false);
    if (!kickAck.ok) {
      expect(kickAck.code).toBe('FORBIDDEN');
    }

    // Guest tries to pause without playback permission
    const pauseAck: Jam2Ack = await new Promise((res) => {
      guestSocket.emit('jam2:cmd', { code, type: 'pause' }, res);
    });
    expect(pauseAck.ok).toBe(false);
    if (!pauseAck.ok) {
      expect(pauseAck.code).toBe('FORBIDDEN');
    }
  });

  it('allows guest with queueAdd to add song, broadcasting state to host', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    if (!createAck.ok) return;
    const code = createAck.room.code;

    await new Promise((res) => guestSocket.emit('jam2:join', { protocol: 2, code }, res));

    const hostStatePromise = new Promise<RoomSnapshot>((res) => {
      hostSocket.once('jam2:state', (s) => res(s));
    });

    const addAck: Jam2Ack<{ version: number }> = await new Promise((res) => {
      guestSocket.emit(
        'jam2:cmd',
        { code, type: 'queueAdd', song: makeTestSong('song_from_guest') },
        res,
      );
    });

    expect(addAck.ok).toBe(true);

    const updatedState = await hostStatePromise;
    expect(updatedState.nowPlaying.item?.song.id).toBe('song_from_guest');
  });

  it('handles host kicking a member: emits jam2:closed { reason: "kicked" } directly to kicked member', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    if (!createAck.ok) return;
    const code = createAck.room.code;

    await new Promise((res) => guestSocket.emit('jam2:join', { protocol: 2, code }, res));

    const kickedPromise = new Promise<any>((res) => {
      guestSocket.once('jam2:closed', (payload) => res(payload));
    });

    const kickAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit('jam2:cmd', { code, type: 'kick', userId: GUEST_USER_ID }, res);
    });

    expect(kickAck.ok).toBe(true);

    const closedPayload = await kickedPromise;
    expect(closedPayload.reason).toBe('kicked');
  });

  it('handles host ending the jam: emits jam2:closed { reason: "ended" } to all', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    if (!createAck.ok) return;
    const code = createAck.room.code;

    await new Promise((res) => guestSocket.emit('jam2:join', { protocol: 2, code }, res));

    const guestClosedPromise = new Promise<any>((res) => {
      guestSocket.once('jam2:closed', (payload) => res(payload));
    });

    const endAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit('jam2:end', { code }, res);
    });

    expect(endAck.ok).toBe(true);

    const guestClosed = await guestClosedPromise;
    expect(guestClosed.reason).toBe('ended');
  });

  it('A1: kick emits jam2:closed to kicked user and jam2:event kicked to bystander without closing bystander', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(createAck.ok).toBe(true);
    if (!createAck.ok) return;
    const code = createAck.room.code;

    // Join target guest
    const targetToken = generateAccessToken({ userId: 'target-kicked-user', username: 'kicked_bob' });
    const targetSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: env.APP_API_KEY, token: targetToken },
      transports: ['websocket'],
      reconnection: false,
    });
    await new Promise<void>((res) => targetSocket.on('connect', () => res()));
    await new Promise((res) => targetSocket.emit('jam2:join', { protocol: 2, code }, res));

    // Join bystander guest
    const bystanderToken = generateAccessToken({ userId: 'bystander-user', username: 'bystander_charlie' });
    const bystanderSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: env.APP_API_KEY, token: bystanderToken },
      transports: ['websocket'],
      reconnection: false,
    });
    await new Promise<void>((res) => bystanderSocket.on('connect', () => res()));
    await new Promise((res) => bystanderSocket.emit('jam2:join', { protocol: 2, code }, res));

    let bystanderReceivedClosed = false;
    bystanderSocket.on('jam2:closed', () => {
      bystanderReceivedClosed = true;
    });

    const bystanderEventPromise = new Promise<any>((res) => {
      bystanderSocket.on('jam2:event', (ev) => {
        if (ev.kind === 'kicked') res(ev);
      });
    });

    const targetClosedPromise = new Promise<any>((res) => {
      targetSocket.once('jam2:closed', (payload) => res(payload));
    });

    // Host kicks target user
    const kickAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit('jam2:cmd', { code, type: 'kick', userId: 'target-kicked-user' }, res);
    });
    expect(kickAck.ok).toBe(true);

    const kickedClosed = await targetClosedPromise;
    expect(kickedClosed.reason).toBe('kicked');

    const bystanderEv = await bystanderEventPromise;
    expect(bystanderEv.kind).toBe('kicked');
    expect(bystanderEv.userId).toBe('target-kicked-user');
    expect(bystanderEv.username).toBe('kicked_bob');

    // Wait a brief tick to ensure bystander never received jam2:closed
    await new Promise((r) => setTimeout(r, 50));
    expect(bystanderReceivedClosed).toBe(false);
    expect(bystanderSocket.connected).toBe(true);

    targetSocket.disconnect();
    bystanderSocket.disconnect();
  });

  it('malformed payloads never crash the server, return INVALID or error ack, and fresh clients succeed', async () => {
    const badPayloads = [
      null,
      undefined,
      123,
      'bad',
      {},
      { code: null },
      { code: 123 },
      { code: 'test', type: 'nonExistentCommand' },
    ];

    const eventNames = ['jam2:create', 'jam2:join', 'jam2:sync', 'jam2:leave', 'jam2:end', 'jam2:cmd'];

    for (const event of eventNames) {
      for (const bad of badPayloads) {
        expect(() => {
          hostSocket.emit(event, bad, (ack?: Jam2Ack) => {
            if (ack) {
              expect(ack.ok).toBe(false);
            }
          });
        }).not.toThrow();
      }
    }

    await new Promise((r) => setTimeout(r, 100));
    expect(hostSocket.connected).toBe(true);

    // Verify fresh client can connect and create a room
    const freshToken = generateAccessToken({ userId: 'fresh_user_id', username: 'fresh_user' });
    const freshSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: env.APP_API_KEY, token: freshToken },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((res) => freshSocket.on('connect', () => res()));
    const freshAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      freshSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(freshAck.ok).toBe(true);
    freshSocket.disconnect();
  });

  it('A5: Song metadata overwritten from provider, client-supplied bad duration and arbitrary imageUrl dropped/overwritten', async () => {
    const createAck: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(createAck.ok).toBe(true);
    if (!createAck.ok) return;
    const code = createAck.room.code;

    // Custom resolver returns authoritative metadata: duration 180, safe saavn URL
    setCustomSongResolver(async (id) => {
      return {
        id,
        title: 'Authoritative Title',
        artists: [{ id: 'art1', name: 'Authoritative Artist' }],
        album: 'Authoritative Album',
        duration: 180,
        imageUrl: 'https://c.saavncdn.com/123/safe.jpg',
        year: '2024',
        language: 'en',
        hasLyrics: false,
        playCount: 100,
        label: 'T-Series',
        streamRef: 'stream_saavn_123',
        providerId: 'jiosaavn',
      };
    });

    const hostStatePromise = new Promise<RoomSnapshot>((res) => {
      hostSocket.once('jam2:state', (s) => res(s));
    });

    // Client attempts to send forged metadata: bad duration 7200, untrusted imageUrl
    const addAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit(
        'jam2:cmd',
        {
          code,
          type: 'queueAdd',
          song: {
            id: 'forged_song_id',
            title: 'Hacked Title',
            artists: [{ id: 'hack', name: 'Hacker' }],
            duration: 7200,
            imageUrl: 'http://malicious-domain.com/hack.jpg',
            providerId: 'jiosaavn',
          },
        },
        res,
      );
    });

    expect(addAck.ok).toBe(true);
    const updatedState = await hostStatePromise;
    const playingSong = updatedState.nowPlaying.item?.song;
    expect(playingSong).toBeDefined();
    // Overwritten by authoritative provider metadata
    expect(playingSong?.duration).toBe(180);
    expect(playingSong?.title).toBe('Authoritative Title');
    expect(playingSong?.imageUrl).toBe('https://c.saavncdn.com/123/safe.jpg');
  });

  it('A7: Create rate limit enforces max 5 rooms per minute per user', async () => {
    // 5 creates succeed
    for (let i = 0; i < 5; i++) {
      const ack: Jam2Ack = await new Promise((res) => {
        hostSocket.emit('jam2:create', { protocol: 2 }, res);
      });
      expect(ack.ok).toBe(true);
    }

    // 6th create fails with RATE_LIMITED
    const sixthAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(sixthAck.ok).toBe(false);
    if (!sixthAck.ok) {
      expect(sixthAck.code).toBe('RATE_LIMITED');
      expect(sixthAck.message).toContain('Too many rooms');
    }
  });

  it('A7: Creating room B when already in room A leaves room A', async () => {
    const ackA: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(ackA.ok).toBe(true);
    if (!ackA.ok) return;
    const codeA = ackA.room.code;

    // Verify user is in room A
    expect(jam2Manager.getActiveRoomForUser(HOST_USER_ID)).toBe(codeA);

    // Host creates room B
    const ackB: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(ackB.ok).toBe(true);
    if (!ackB.ok) return;
    const codeB = ackB.room.code;

    // User should now be tracked in room B and NOT in room A
    expect(jam2Manager.getActiveRoomForUser(HOST_USER_ID)).toBe(codeB);
    expect(jam2Manager.getUserSockets(codeA, HOST_USER_ID).length).toBe(0);
  });

  it('G9.b (F7): failed join keeps user in room A; successful join room B leaves room A', async () => {
    // Host creates room A
    const ackA: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(ackA.ok).toBe(true);
    if (!ackA.ok) return;
    const codeA = ackA.room.code;

    // Guest joins room A
    const joinA: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      guestSocket.emit('jam2:join', { protocol: 2, code: codeA }, res);
    });
    expect(joinA.ok).toBe(true);

    // Verify guest is tracked in room A and online
    expect(jam2Manager.getActiveRoomForUser(GUEST_USER_ID)).toBe(codeA);
    const roomA = await jam2Manager.getRoom(codeA);
    expect(roomA?.members.get(GUEST_USER_ID)?.online).toBe(true);

    // Guest attempts to join a non-existent room code
    const joinFail: Jam2Ack = await new Promise((res) => {
      guestSocket.emit('jam2:join', { protocol: 2, code: 'ZZZZ' }, res);
    });
    expect(joinFail.ok).toBe(false);
    if (!joinFail.ok) {
      expect(joinFail.code).toBe('NOT_FOUND');
    }

    // Verify guest is STILL a member and online in room A
    expect(jam2Manager.getActiveRoomForUser(GUEST_USER_ID)).toBe(codeA);
    const roomAAfter = await jam2Manager.getRoom(codeA);
    expect(roomAAfter?.members.has(GUEST_USER_ID)).toBe(true);
    expect(roomAAfter?.members.get(GUEST_USER_ID)?.online).toBe(true);

    // Create room B via manager
    const roomBEntity = await jam2Manager.createRoom('other_host', 'OtherHost');
    const codeB = roomBEntity.code;

    // Guest joins room B successfully
    const joinB: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      guestSocket.emit('jam2:join', { protocol: 2, code: codeB }, res);
    });
    expect(joinB.ok).toBe(true);

    // Verify guest is now tracked in room B and REMOVED from room A
    expect(jam2Manager.getActiveRoomForUser(GUEST_USER_ID)).toBe(codeB);
    const roomBAfter = await jam2Manager.getRoom(codeB);
    expect(roomBAfter?.members.has(GUEST_USER_ID)).toBe(true);
    const roomAFinal = await jam2Manager.getRoom(codeA);
    expect(roomAFinal?.members.has(GUEST_USER_ID)).toBe(false);
  });

  it('G9.a: non-member and unprivileged user get FORBIDDEN and music resolver is never called', async () => {
    // Host creates room
    const ackA: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      hostSocket.emit('jam2:create', { protocol: 2 }, res);
    });
    expect(ackA.ok).toBe(true);
    if (!ackA.ok) return;
    const codeA = ackA.room.code;

    const resolverSpy = vi.spyOn(songResolverModule, 'resolveSongOrFallback');

    // Case 1: Guest is NOT a member of room A, attempts queueAdd
    const nonMemberAck: Jam2Ack = await new Promise((res) => {
      guestSocket.emit(
        'jam2:cmd',
        {
          code: codeA,
          type: 'queueAdd',
          song: makeTestSong('song_non_member'),
        },
        res,
      );
    });
    expect(nonMemberAck.ok).toBe(false);
    if (!nonMemberAck.ok) {
      expect(nonMemberAck.code).toBe('FORBIDDEN');
    }
    expect(resolverSpy).not.toHaveBeenCalled();

    // Guest joins room A
    const joinA: Jam2Ack<{ room: RoomSnapshot }> = await new Promise((res) => {
      guestSocket.emit('jam2:join', { protocol: 2, code: codeA }, res);
    });
    expect(joinA.ok).toBe(true);

    // Host demotes guest (revokes queueAdd permission)
    const demoteAck: Jam2Ack = await new Promise((res) => {
      hostSocket.emit(
        'jam2:cmd',
        {
          code: codeA,
          type: 'setMemberPerms',
          userId: GUEST_USER_ID,
          perms: { queueAdd: false, playback: false, queueManage: false },
        },
        res,
      );
    });
    expect(demoteAck.ok).toBe(true);

    // Case 2: Guest is in room A but does NOT have queueAdd permission
    resolverSpy.mockClear();
    const unprivilegedAck: Jam2Ack = await new Promise((res) => {
      guestSocket.emit(
        'jam2:cmd',
        {
          code: codeA,
          type: 'queueAdd',
          song: makeTestSong('song_unprivileged'),
        },
        res,
      );
    });
    expect(unprivilegedAck.ok).toBe(false);
    if (!unprivilegedAck.ok) {
      expect(unprivilegedAck.code).toBe('FORBIDDEN');
    }
    expect(resolverSpy).not.toHaveBeenCalled();

    resolverSpy.mockRestore();
  });
});

