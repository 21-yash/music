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
