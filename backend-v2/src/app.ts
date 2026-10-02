import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { env } from './config/env';
import { requestLogger, notFoundHandler, errorHandler } from './middleware';
import apiV1Router from './routes';

/**
 * Create and configure the Express application.
 *
 * This function is separated from server.ts so the app can be imported
 * independently for testing (e.g., supertest) without starting the
 * HTTP server or connecting to databases.
 */
export function createApp(): express.Application {
  const app = express();

  // ─── Security ───────────────────────────────────────────────────
  app.use(helmet());

  // ─── CORS ───────────────────────────────────────────────────────
  const origins = env.CORS_ORIGINS.split(',').map((o) => o.trim());
  app.use(
    cors({
      origin(origin, callback) {
        // Allow requests with no origin (mobile apps, Postman, curl)
        if (!origin) return callback(null, true);
        if (origins.includes(origin)) return callback(null, true);
        return callback(null, false);
      },
      credentials: true,
      methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
      allowedHeaders: ['Content-Type', 'Authorization'],
    }),
  );

  // ─── Body parsing ──────────────────────────────────────────────
  // Reasonable limit to prevent abuse; can be adjusted per-route later
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: true, limit: '1mb' }));

  // ─── Request logging ───────────────────────────────────────────
  app.use(requestLogger);

  // ─── API routes ────────────────────────────────────────────────
  app.use('/api/v1', apiV1Router);

  // ─── 404 handler (after all routes) ────────────────────────────
  app.use(notFoundHandler);

  // ─── Centralized error handler (must be last) ──────────────────
  app.use(errorHandler);

  return app;
}
