import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import http from 'http';
import { io as ioClient, Socket as ClientSocket } from 'socket.io-client';
import { createApp } from '../../src/app';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';
import { initializeSocketIO } from '../../src/websocket';
import { Jam2Ack, RoomSnapshot, Song } from '../../src/jam2/types';
import { jam2Manager } from '../../src/jam2/manager';

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
  });

  afterAll(async () => {
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

  it('malformed payloads never crash the server and return INVALID', async () => {
    const badPayloads = [null, undefined, 123, 'bad', { code: 'test', type: 'nonExistentCommand' }];

    for (const bad of badPayloads) {
      const ack: Jam2Ack = await new Promise((res) => {
        hostSocket.emit('jam2:cmd', bad, res);
      });
      expect(ack.ok).toBe(false);
      if (!ack.ok) {
        expect(ack.code).toBe('INVALID');
      }
    }
  });
});

