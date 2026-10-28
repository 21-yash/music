import { Request, Response, NextFunction, RequestHandler } from 'express';
import rateLimit from 'express-rate-limit';
import { RedisStore } from 'rate-limit-redis';
import { getRedisClient } from '../config/redis';
import { TooManyRequestsError } from '../utils/errors';
import { logger } from '../utils/logger';

interface LazyLimiterOptions {
  windowMs: number;
  limit: number;
  keyPrefix: string;
  keyGenerator?: (req: Request) => string;
  skip?: (req: Request) => boolean;
  skipSuccessfulRequests?: boolean;
}

/**
 * Creates a rate limiter middleware that is initialized lazily on the first request.
 * This prevents calling getRedisClient() during module import time before Redis is connected.
 * In test mode (NODE_ENV === 'test'), rate limiting is bypassed.
 */
function createLazyLimiter(options: LazyLimiterOptions): RequestHandler {
  let limiterInstance: RequestHandler | null = null;

  return (req: Request, res: Response, next: NextFunction) => {
    if (process.env.NODE_ENV === 'test') {
      return next();
    }

    if (!limiterInstance) {
      limiterInstance = rateLimit({
        windowMs: options.windowMs,
        limit: options.limit,
        standardHeaders: true,
        legacyHeaders: false,
        passOnStoreError: true, // Allow traffic if Redis becomes unavailable
        validate: {
          creationStack: false,
          keyGeneratorIpFallback: false,
        },
        skip: options.skip,
        skipSuccessfulRequests: options.skipSuccessfulRequests,
        keyGenerator: options.keyGenerator,
        store: new RedisStore({
          sendCommand: async (cmd: string, ...args: string[]) => {
            return (await getRedisClient().call(cmd, ...args)) as any;
          },
          prefix: `rl:${options.keyPrefix}:`,
        }),
        handler: (req, _res, next) => {
          logger.warn({ ip: req.ip, path: req.path }, 'Rate limit exceeded');
          next(new TooManyRequestsError('Rate limit exceeded. Please try again later.'));
        },
      });
    }

    return limiterInstance(req, res, next);
  };
}

// ─── Exported Limiter Middlewares ─────────────────────────────────────

/** Global: 1000 req / 15 min per IP, skipping health checks */
export const globalLimiter = createLazyLimiter({
  windowMs: 15 * 60 * 1000,
  limit: 1000,
  keyPrefix: 'global',
  skip: (req) => req.path.includes('/health') || Boolean(req.originalUrl?.includes('/health')),
});

/** Music routes general: 120 req / 1 min per IP */
export const musicLimiter = createLazyLimiter({
  windowMs: 60 * 1000,
  limit: 120,
  keyPrefix: 'music',
});

/** Music search: 120 req / 1 min per IP */
export const searchLimiter = createLazyLimiter({
  windowMs: 60 * 1000,
  limit: 120,
  keyPrefix: 'search',
});

/** Music stream: 120 req / 1 min per IP */
export const streamLimiter = createLazyLimiter({
  windowMs: 60 * 1000,
  limit: 120,
  keyPrefix: 'stream',
});

/** Sports routes: 120 req / 1 min per IP */
export const sportsLimiter = createLazyLimiter({
  windowMs: 60 * 1000,
  limit: 120,
  keyPrefix: 'sports',
});

/** Auth login (per IP + account): 10 req / 15 min, skip successful logins */
export const loginLimiter = createLazyLimiter({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  keyPrefix: 'login',
  keyGenerator: (req) => `${req.ip ?? 'unknown'}:${String(req.body?.login ?? '').trim().toLowerCase()}`,
  skipSuccessfulRequests: true,
});

/** Auth login (per IP): 30 req / 15 min */
export const loginIpLimiter = createLazyLimiter({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  keyPrefix: 'login_ip',
});

/** Auth register: 5 req / 1 hour per IP */
export const registerLimiter = createLazyLimiter({
  windowMs: 60 * 60 * 1000,
  limit: 5,
  keyPrefix: 'register',
});

/** Auth refresh: 30 req / 15 min per IP */
export const refreshLimiter = createLazyLimiter({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  keyPrefix: 'refresh',
});
