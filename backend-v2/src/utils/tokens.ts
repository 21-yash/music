import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { env } from '../config/env';

/**
 * JWT token utilities.
 *
 * Architecture:
 * - Access tokens: short-lived (15 min), used for API authorization.
 *   Stateless — not stored server-side.
 * - Refresh tokens: long-lived (30 days), used to obtain new access tokens.
 *   Stored as bcrypt hashes in the User document for revocability.
 *
 * The refresh token is a random opaque string (not a JWT) to avoid
 * confusion about which token to send where.
 */

export interface AccessTokenPayload {
  userId: string;
  username: string;
}

const ACCESS_TOKEN_EXPIRY = '15m';
const REFRESH_TOKEN_EXPIRY_DAYS = 30;

/**
 * Generate a signed JWT access token.
 */
export function generateAccessToken(payload: AccessTokenPayload): string {
  return jwt.sign(payload, env.JWT_ACCESS_SECRET, {
    expiresIn: ACCESS_TOKEN_EXPIRY,
  });
}

/**
 * Verify and decode a JWT access token.
 * Throws if the token is invalid or expired.
 */
export function verifyAccessToken(token: string): AccessTokenPayload {
  return jwt.verify(token, env.JWT_ACCESS_SECRET) as AccessTokenPayload;
}

/**
 * Generate a cryptographically random refresh token.
 * Returns the raw token (to send to the client) — the caller is
 * responsible for hashing it before storage.
 */
export function generateRefreshToken(): string {
  return crypto.randomBytes(40).toString('hex');
}

/**
 * Calculate the expiry date for a new refresh token.
 */
export function getRefreshTokenExpiry(): Date {
  const expiry = new Date();
  expiry.setDate(expiry.getDate() + REFRESH_TOKEN_EXPIRY_DAYS);
  return expiry;
}

export { ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY_DAYS };
