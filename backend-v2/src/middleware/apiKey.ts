import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { env } from '../config/env';
import { ForbiddenError } from '../utils/errors';
import { logger } from '../utils/logger';

/**
 * Validates the X-Api-Key header using constant-time comparison.
 * Always throws ForbiddenError (403, NEVER 401) on missing or invalid key
 * to prevent triggering the Android OkHttp AuthAuthenticator logout/refresh loop.
 */
export function requireApiKey(req: Request, _res: Response, next: NextFunction): void {
  const rawKey = req.headers['x-api-key'];
  const key = typeof rawKey === 'string' ? rawKey : undefined;

  const isValid =
    key !== undefined &&
    Buffer.byteLength(key) === Buffer.byteLength(env.APP_API_KEY) &&
    crypto.timingSafeEqual(Buffer.from(key), Buffer.from(env.APP_API_KEY));

  if (!isValid) {
    logger.warn({ ip: req.ip, path: req.path }, 'Invalid or missing API key');
    throw new ForbiddenError('Invalid or missing API key');
  }

  next();
}
