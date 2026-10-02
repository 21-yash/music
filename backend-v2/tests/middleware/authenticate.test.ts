import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';

// Mock env before importing authenticate
vi.mock('../../src/config/env', () => ({
  env: {
    JWT_ACCESS_SECRET: 'test-access-secret',
    JWT_REFRESH_SECRET: 'test-refresh-secret',
    NODE_ENV: 'test',
  },
}));

import { authenticate } from '../../src/middleware/authenticate';

function createMockReqResNext(authHeader?: string) {
  const req = {
    headers: {
      authorization: authHeader,
    },
  } as unknown as Request;
  const res = {
    status: vi.fn().mockReturnThis(),
    json: vi.fn().mockReturnThis(),
  } as unknown as Response;
  const next = vi.fn() as NextFunction;
  return { req, res, next };
}

describe('authenticate middleware', () => {
  it('throws UnauthorizedError when no Authorization header', () => {
    const { req, res, next } = createMockReqResNext();
    expect(() => authenticate(req, res, next)).toThrow('Access token is required');
  });

  it('throws UnauthorizedError when header does not start with Bearer', () => {
    const { req, res, next } = createMockReqResNext('Token abc123');
    expect(() => authenticate(req, res, next)).toThrow('Access token is required');
  });

  it('throws UnauthorizedError for an invalid token', () => {
    const { req, res, next } = createMockReqResNext('Bearer invalid.token.here');
    expect(() => authenticate(req, res, next)).toThrow('Invalid or expired access token');
  });

  it('attaches user payload and calls next() for a valid token', () => {
    const payload = { userId: 'user123', username: 'testuser' };
    const token = jwt.sign(payload, 'test-access-secret', { expiresIn: '15m' });

    const { req, res, next } = createMockReqResNext(`Bearer ${token}`);
    authenticate(req, res, next);

    expect(next).toHaveBeenCalled();
    expect(req.user).toBeDefined();
    expect(req.user!.userId).toBe('user123');
    expect(req.user!.username).toBe('testuser');
  });

  it('throws UnauthorizedError for an expired token', () => {
    const payload = { userId: 'user123', username: 'testuser' };
    const token = jwt.sign(payload, 'test-access-secret', { expiresIn: '0s' });

    const { req, res, next } = createMockReqResNext(`Bearer ${token}`);
    expect(() => authenticate(req, res, next)).toThrow('Invalid or expired access token');
  });
});
