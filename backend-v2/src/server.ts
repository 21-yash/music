import http from 'http';
import { createApp } from './app';
import { env } from './config/env';
import { connectMongoDB, disconnectMongoDB } from './config/database';
import { connectRedis, disconnectRedis } from './config/redis';
import { initializeSocketIO } from './websocket';
import { logger } from './utils/logger';
import { initMusicService } from './services/musicService';
import { SpotifyProvider } from './providers/music';

/**
 * Application entry point.
 *
 * Responsibilities:
 * 1. Connect to external services (MongoDB, Redis).
 * 2. Create the Express app and HTTP server.
 * 3. Initialize Socket.IO.
 * 4. Start listening.
 * 5. Handle graceful shutdown on SIGTERM / SIGINT.
 */

async function start(): Promise<void> {
  try {
    // ─── Connect to databases ──────────────────────────────────
    await connectMongoDB();
    await connectRedis();

    // ─── Initialize Services ───────────────────────────────────
    initMusicService(new SpotifyProvider());

    // ─── Create Express app & HTTP server ──────────────────────
    const app = createApp();
    const server = http.createServer(app);

    // ─── Initialize Socket.IO ──────────────────────────────────
    initializeSocketIO(server);

    // ─── Start listening ───────────────────────────────────────
    server.listen(env.PORT, () => {
      logger.info(
        {
          port: env.PORT,
          environment: env.NODE_ENV,
          api: `http://localhost:${env.PORT}/api/v1`,
        },
        'Server started',
      );
    });

    // ─── Graceful shutdown ─────────────────────────────────────
    const shutdown = async (signal: string) => {
      logger.info({ signal }, 'Shutdown signal received');

      // Stop accepting new connections
      server.close(async () => {
        logger.info('HTTP server closed');

        // Close database connections
        await Promise.allSettled([disconnectMongoDB(), disconnectRedis()]);

        logger.info('All connections closed. Exiting.');
        process.exit(0);
      });

      // Force exit if graceful shutdown takes too long
      setTimeout(() => {
        logger.error('Graceful shutdown timed out. Forcing exit.');
        process.exit(1);
      }, 10_000);
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));

    // ─── Uncaught exception / unhandled rejection safety net ───
    process.on('uncaughtException', (error) => {
      logger.fatal({ error }, 'Uncaught exception');
      process.exit(1);
    });

    process.on('unhandledRejection', (reason) => {
      logger.fatal({ reason }, 'Unhandled rejection');
      process.exit(1);
    });
  } catch (error) {
    logger.fatal({ error }, 'Failed to start server');
    process.exit(1);
  }
}

start();
