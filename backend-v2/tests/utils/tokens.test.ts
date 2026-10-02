import { describe, it, expect, vi, beforeEach } from 'vitest';
import jwt from 'jsonwebtoken';

// Mock env before importing
vi.mock('../../src/config/env', () => ({
  env: {
    JWT_ACCESS_SECRET: 'test-access-secret-key-for-testing',
    JWT_REFRESH_SECRET: 'test-refresh-secret-key-for-testing',
    NODE_ENV: 'test',
  },
}));

import {
  generateAccessToken,
  verifyAccessToken,
  generateRefreshToken,
  getRefreshTokenExpiry,
} from '../../src/utils/tokens';

describe('generateAccessToken', () => {
  it('returns a valid JWT string', () => {
    const token = generateAccessToken({ userId: 'abc123', username: 'testuser' });
    expect(typeof token).toBe('string');
    expect(token.split('.')).toHaveLength(3); // JWT has 3 parts
  });

  it('encodes the correct payload', () => {
    const token = generateAccessToken({ userId: 'user1', username: 'john' });
    const decoded = jwt.decode(token) as Record<string, unknown>;
    expect(decoded.userId).toBe('user1');
    expect(decoded.username).toBe('john');
  });

  it('sets an expiry (exp claim)', () => {
    const token = generateAccessToken({ userId: 'user1', username: 'john' });
    const decoded = jwt.decode(token) as Record<string, unknown>;
    expect(decoded.exp).toBeDefined();
  });
});

describe('verifyAccessToken', () => {
  it('returns the payload for a valid token', () => {
    const token = generateAccessToken({ userId: 'user1', username: 'john' });
    const payload = verifyAccessToken(token);
    expect(payload.userId).toBe('user1');
    expect(payload.username).toBe('john');
  });

  it('throws for an invalid token', () => {
    expect(() => verifyAccessToken('invalid.token.value')).toThrow();
  });

  it('throws for a token signed with a different secret', () => {
    const token = jwt.sign(
      { userId: 'user1', username: 'john' },
      'wrong-secret',
    );
    expect(() => verifyAccessToken(token)).toThrow();
  });
});

describe('generateRefreshToken', () => {
  it('returns a hex string', () => {
    const token = generateRefreshToken();
    expect(typeof token).toBe('string');
    expect(token).toMatch(/^[a-f0-9]+$/);
  });

  it('returns 80 characters (40 bytes in hex)', () => {
    const token = generateRefreshToken();
    expect(token.length).toBe(80);
  });

  it('generates unique tokens', () => {
    const t1 = generateRefreshToken();
    const t2 = generateRefreshToken();
    expect(t1).not.toBe(t2);
  });
});

describe('getRefreshTokenExpiry', () => {
  it('returns a date ~30 days in the future', () => {
    const now = Date.now();
    const expiry = getRefreshTokenExpiry();
    const thirtyDaysMs = 30 * 24 * 60 * 60 * 1000;
    const diff = expiry.getTime() - now;

    // Allow 5 seconds of tolerance
    expect(diff).toBeGreaterThan(thirtyDaysMs - 5000);
    expect(diff).toBeLessThan(thirtyDaysMs + 5000);
  });
});
