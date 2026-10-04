import { Server as HttpServer } from 'http';
import { Server as SocketIOServer } from 'socket.io';
import { env } from '../config/env';
import { logger } from '../utils/logger';
import { registerSportsHandlers } from './sportsHandler';
import { SportsWorker } from '../jobs/sportsWorker';

let io: SocketIOServer | null = null;
let sportsWorker: SportsWorker | null = null;

/**
 * Initialize Socket.IO and attach it to the HTTP server.
 */
export function initializeSocketIO(httpServer: HttpServer): SocketIOServer {
  const origins = env.CORS_ORIGINS.split(',').map((o) => o.trim());

  io = new SocketIOServer(httpServer, {
    cors: {
      origin: origins.includes('*') ? true : origins,
      methods: ['GET', 'POST'],
      credentials: true,
    },
    pingInterval: 25_000,
    pingTimeout: 20_000,
  });

  // Start the demand-driven sports worker
  sportsWorker = new SportsWorker(io, 10_000); // 10 second polling interval
  sportsWorker.start();

  io.on('connection', (socket) => {
    logger.info({ socketId: socket.id }, 'Socket.IO client connected');

    // Register domain handlers
    registerSportsHandlers(socket);

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

