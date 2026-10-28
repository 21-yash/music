import { Server as HttpServer } from 'http';
import { Server as SocketIOServer } from 'socket.io';
import crypto from 'crypto';
import { env } from '../config/env';
import { logger } from '../utils/logger';
import { verifyAccessToken } from '../utils/tokens';
import { registerSportsHandlers } from './sportsHandler';
import { registerJamHandlers } from './jamHandler';
import { SportsWorker } from '../jobs/sportsWorker';

let io: SocketIOServer | null = null;
let sportsWorker: SportsWorker | null = null;

/**
 * Initialize Socket.IO and attach it to the HTTP server.
 */
export function initializeSocketIO(httpServer: HttpServer): SocketIOServer {
  const origins = env.CORS_ORIGINS.split(',').map((o) => o.trim());

  const server = new SocketIOServer(httpServer, {
    cors: {
      origin: origins.includes('*') ? true : origins,
      methods: ['GET', 'POST'],
      credentials: true,
    },
    maxHttpBufferSize: 50_000,
    pingInterval: 10_000,
    pingTimeout: 8_000,
  });
  io = server;

  // Socket.IO authentication middleware: verifies API key and JWT access token at handshake
  server.use((socket, next) => {
    try {
      const apiKey = socket.handshake.auth?.apiKey;
      const isValidKey =
        typeof apiKey === 'string' &&
        Buffer.byteLength(apiKey) === Buffer.byteLength(env.APP_API_KEY) &&
        crypto.timingSafeEqual(Buffer.from(apiKey), Buffer.from(env.APP_API_KEY));

      if (!isValidKey) {
        logger.warn(
          { ip: socket.handshake.address },
          'Socket.IO connection rejected: invalid or missing API key',
        );
        return next(new Error('Invalid or missing API key'));
      }

      const token = socket.handshake.auth?.token;
      if (typeof token !== 'string' || !token) {
        logger.warn(
          { ip: socket.handshake.address },
          'Socket.IO connection rejected: missing auth token',
        );
        return next(new Error('Authentication token required'));
      }

      const payload = verifyAccessToken(token);
      socket.data.user = {
        userId: payload.userId,
        username: payload.username,
      };

      next();
    } catch (err) {
      logger.warn(
        { ip: socket.handshake.address, err },
        'Socket.IO connection rejected: invalid auth token',
      );
      next(new Error('Invalid or expired authentication token'));
    }
  });

  // Start the demand-driven sports worker
  sportsWorker = new SportsWorker(server, 15_000); // 15 second polling interval
  sportsWorker.start();

  server.on('connection', (socket) => {
    logger.info({ socketId: socket.id }, 'Socket.IO client connected');

    // Register domain handlers
    registerSportsHandlers(socket);
    registerJamHandlers(server, socket);

    socket.on('disconnect', (reason) => {
      logger.info(
        { socketId: socket.id, reason },
        'Socket.IO client disconnected',
      );
    });

    socket.on('error', (error) => {
      logger.error(
        { socketId: socket.id, error },
        'Socket.IO client error',
      );
    });
  });

  logger.info('Socket.IO server initialized');
  return io;
}

