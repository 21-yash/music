import { Request, Response, NextFunction } from 'express';
import { verifyAccessToken, AccessTokenPayload } from '../utils/tokens';
import { UnauthorizedError } from '../utils/errors';

/**
 * Extend Express Request to carry the authenticated user's identity.
 */
declare global {
  namespace Express {
    interface Request {
      user?: AccessTokenPayload;
    }
  }
}

/**
 * Authentication middleware.
 *
 * Extracts the Bearer token from the Authorization header, verifies it
 * as a valid JWT access token, and attaches the decoded payload to
 * `req.user`. Throws UnauthorizedError if the token is missing, invalid,
 * or expired.
 */
export function authenticate(
  req: Request,
  _res: Response,
  next: NextFunction,
): void {
  const header = req.headers.authorization;

  if (!header || !header.startsWith('Bearer ')) {
    throw new UnauthorizedError('Access token is required');
  }

  const token = header.slice(7); // Remove 'Bearer '

  try {
    const payload = verifyAccessToken(token);
    req.user = payload;
    next();
  } catch {
    throw new UnauthorizedError('Invalid or expired access token');
  }
}
