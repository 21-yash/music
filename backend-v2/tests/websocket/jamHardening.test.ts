import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import http from 'http';
import { io as ioClient, Socket as ClientSocket } from 'socket.io-client';
import { createApp } from '../../src/app';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';
import { initializeSocketIO } from '../../src/websocket';

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

  it('Malformed payloads to jam handlers never throw or crash the server', async () => {
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
  });
});
