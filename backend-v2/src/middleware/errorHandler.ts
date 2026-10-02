import { Request, Response, NextFunction } from 'express';
import { AppError } from '../utils/errors';
import { sendError } from '../utils/response';
import { logger } from '../utils/logger';
import { env } from '../config/env';

/**
 * Centralized error-handling middleware.
 *
 * Must be registered LAST in the middleware chain (after all routes).
 *
 * Behaviour:
 * - AppError (operational): returns the error's statusCode, code, and message.
 * - Unknown error: returns a generic 500. Stack traces are never exposed
 *   in production — they're logged server-side only.
 */
export function errorHandler(
  err: Error,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  // AppError — expected, operational errors
  if (err instanceof AppError) {
    if (!err.isOperational) {
      // Programming / unexpected error that happened to be wrapped
      logger.error({ err, stack: err.stack }, 'Non-operational AppError');
    } else {
      logger.warn({ code: err.code, message: err.message }, 'Operational error');
    }

    sendError(res, err.statusCode, err.code, err.message);
    return;
  }

  // Unexpected / unknown error — never expose internals to the client
  logger.error({ err, stack: err.stack }, 'Unhandled error');

  const message =
    env.NODE_ENV === 'development'
      ? err.message
      : 'An unexpected error occurred';

  sendError(res, 500, 'INTERNAL_ERROR', message);
}
