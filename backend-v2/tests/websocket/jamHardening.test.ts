import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import http from 'http';
import { io as ioClient, Socket as ClientSocket } from 'socket.io-client';
import { createApp } from '../../src/app';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';
import { initializeSocketIO } from '../../src/websocket';
import { activeSessions } from '../../src/websocket/jamHandler';

describe('Jam WebSocket Hardening (Finding S1 & S5)', () => {
  let server: http.Server;
  let port: number;
  let clientSocket: ClientSocket;

  const VALID_KEY = env.APP_API_KEY;
  const VALID_TOKEN = generateAccessToken({
    userId: 'user-hardening-test-123456789012',
    username: 'hardening_user',
  });

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

    clientSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: VALID_KEY, token: VALID_TOKEN },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => {
      clientSocket.on('connect', () => resolve());
    });
  });

  afterAll(async () => {
    if (clientSocket && clientSocket.connected) {
      clientSocket.disconnect();
    }
    await new Promise<void>((resolve) => {
      server.close(() => resolve());
    });
  });

  it('Malformed payloads to jam handlers never throw or crash the server, and server serves fresh clients', async () => {
    const malformedPayloads = [
      null,
      undefined,
      12345,
      'invalid-string-payload',
      [],
      {},
      { code: null },
      { code: 123 },
      { code: {} },
    ];

    const eventNames = [
      'jam:create',
      'jam:join',
      'jam:sync_playback',
      'jam:add_to_queue',
      'jam:remove_from_queue',
      'jam:reorder_queue',
      'jam:set_permissions',
      'jam:leave',
    ];

    // Emit all malformed combinations
    for (const event of eventNames) {
      for (const payload of malformedPayloads) {
        expect(() => {
          clientSocket.emit(event, payload);
        }).not.toThrow();
      }
    }

    // Small delay to let the server process all events
    await new Promise((r) => setTimeout(r, 200));

    // Server is still alive and responsive
    expect(clientSocket.connected).toBe(true);

    // Open a second fresh authenticated socket and verify room creation succeeds
    const secondSocket = ioClient(`http://localhost:${port}`, {
      auth: {
        apiKey: VALID_KEY,
        token: generateAccessToken({
          userId: 'user-fresh-client-567890123456',
          username: 'fresh_user',
        }),
      },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => {
      secondSocket.on('connect', () => resolve());
    });

    const createPayload = await new Promise<any>((resolve) => {
      secondSocket.on('jam:created', (data) => resolve(data));
      secondSocket.emit('jam:create', {});
    });

    expect(createPayload).toBeDefined();
    expect(createPayload.session?.code).toBeDefined();
    expect(createPayload.session?.hostUserId).toBe('user-fresh-client-567890123456');

    secondSocket.disconnect();
  });

  it('Socket disconnect with reason "client namespace disconnect" does NOT immediately remove host (uses grace period)', async () => {
    const hostToken = generateAccessToken({
      userId: 'user-host-grace-111111111111',
      username: 'host_grace',
    });
    const guestToken = generateAccessToken({
      userId: 'user-guest-grace-222222222222',
      username: 'guest_grace',
    });

    const hostSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: VALID_KEY, token: hostToken },
      transports: ['websocket'],
      reconnection: false,
    });
    await new Promise<void>((resolve) => hostSocket.on('connect', () => resolve()));

    const createPayload = await new Promise<any>((resolve) => {
      hostSocket.on('jam:created', (data) => resolve(data));
      hostSocket.emit('jam:create', {});
    });
    const code = createPayload.session.code;

    const guestSocket = ioClient(`http://localhost:${port}`, {
      auth: { apiKey: VALID_KEY, token: guestToken },
      transports: ['websocket'],
      reconnection: false,
    });
    await new Promise<void>((resolve) => guestSocket.on('connect', () => resolve()));

    await new Promise<any>((resolve) => {
      guestSocket.on('jam:joined', (data) => resolve(data));
      guestSocket.emit('jam:join', { code });
    });

    let hostChangedFired = false;
    guestSocket.on('jam:host_changed', () => {
      hostChangedFired = true;
    });

    // Host disconnects via client namespace disconnect (e.g. Wi-Fi <-> 4G handover)
    hostSocket.disconnect();

    // Small delay to ensure server processed the disconnect event
    await new Promise((r) => setTimeout(r, 200));

    // The session must still exist and the host must NOT have been immediately removed or transferred
    const session = activeSessions.get(code);
    expect(session).toBeDefined();
    expect(session?.hostUserId).toBe('user-host-grace-111111111111');
    expect(hostChangedFired).toBe(false);

    guestSocket.disconnect();
  });
});
