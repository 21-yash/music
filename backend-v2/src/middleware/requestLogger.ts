import { Request, Response, NextFunction } from 'express';
import { logger } from '../utils/logger';

/**
 * Simple request logging middleware.
 *
 * Logs method, url, status code, and response time for every request.
 * Uses pino for structured JSON output.
 */
export function requestLogger(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  const start = Date.now();

  res.on('finish', () => {
    const duration = Date.now() - start;
    const limiterWaitMs = (req as any).limiterWaitMs;

    if (duration > 1500) {
      logger.warn(
        {
          method: req.method,
          path: req.originalUrl.split('?')[0],
          statusCode: res.statusCode,
          durationMs: duration,
          ...(limiterWaitMs !== undefined ? { limiterWaitMs } : {}),
        },
        'Slow request detected (> 1500ms)'
      );
    } else if (limiterWaitMs !== undefined && limiterWaitMs > 200) {
      logger.warn(
        {
          method: req.method,
          path: req.originalUrl.split('?')[0],
          limiterWaitMs,
        },
        'Slow limiter wait detected (> 200ms)'
      );
    }

    logger.info(
      {
        method: req.method,
        url: req.originalUrl,
        statusCode: res.statusCode,
        durationMs: duration,
      },
      'Request completed',
    );
  });

  next();
}
