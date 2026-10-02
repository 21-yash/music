import { Server as HttpServer } from 'http';
import { Server as SocketIOServer } from 'socket.io';
import { env } from '../config/env';
import { logger } from '../utils/logger';

/**
 * Initialize Socket.IO and attach it to the HTTP server.
 *
 * At this phase the Socket.IO server is set up with CORS configuration
 * and basic connection/disconnect logging. Future phases will add
 * authentication middleware, room management, and event handlers.
 *
 * Design decision: Socket.IO distributes state/events — it is NOT the
 * source of truth. Redis/MongoDB/provider state is authoritative.
 * After a reconnect, clients must resynchronize authoritative state
 * rather than assume they received every missed event.
 */
export function initializeSocketIO(httpServer: HttpServer): SocketIOServer {
  const origins = env.CORS_ORIGINS.split(',').map((o) => o.trim());

  const io = new SocketIOServer(httpServer, {
    cors: {
      origin: origins,
      methods: ['GET', 'POST'],
      credentials: true,
    },
    pingInterval: 25_000,
    pingTimeout: 20_000,
  });

  io.on('connection', (socket) => {
    logger.info({ socketId: socket.id }, 'Socket.IO client connected');

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
