import { Request, Response } from 'express';
import { sendError } from '../utils/response';
import { logger } from '../utils/logger';

/**
 * 404 handler — must be registered after all route definitions
 * but before the centralized error handler.
 */
export function notFoundHandler(req: Request, res: Response): void {
  logger.debug({ method: req.method, path: req.path }, 'Route not found');
  sendError(res, 404, 'NOT_FOUND', `Route ${req.method} ${req.path} not found`);
}
