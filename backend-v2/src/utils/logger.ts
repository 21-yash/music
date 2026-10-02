import pino from 'pino';
import { env } from '../config/env';

/**
 * Structured JSON logger using pino.
 *
 * - In development: pretty-printed output via pino-pretty.
 * - In production/test: raw JSON for machine consumption.
 *
 * Security: never log JWTs, passwords, refresh tokens, or other secrets.
 * Callers must redact sensitive fields before passing objects to the logger.
 */
export const logger = pino({
  level: env.NODE_ENV === 'test' ? 'silent' : 'info',
  transport:
    env.NODE_ENV === 'development'
      ? {
          target: 'pino-pretty',
          options: {
            colorize: true,
            translateTime: 'SYS:HH:MM:ss',
            ignore: 'pid,hostname',
          },
        }
      : undefined,
  // Redact sensitive fields that may accidentally appear in log objects
  redact: {
    paths: [
      'password',
      'accessToken',
      'refreshToken',
      'authorization',
      'req.headers.authorization',
    ],
    censor: '[REDACTED]',
  },
});
