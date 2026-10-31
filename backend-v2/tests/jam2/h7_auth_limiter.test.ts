import { describe, it, expect, vi, beforeEach } from 'vitest';
import bcrypt from 'bcryptjs';
import * as authService from '../../src/services/authService';
import { User } from '../../src/models/User';
import { createLazyLimiter } from '../../src/middleware/rateLimit';

describe('Task H7 backend auth refresh and limiter fail-open', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('refresh with 15 legacy bcrypt tokens still works and migrates the matched one to sha256', async () => {
    const rawTargetToken = 'legacy-target-token-12345';
    const legacyHash = await bcrypt.hash(rawTargetToken, 1); // low cost for test

    const tokens: any[] = [];
    for (let i = 0; i < 14; i++) {
      tokens.push({
        _id: `token-${i}`,
        tokenHash: await bcrypt.hash(`legacy-other-${i}`, 1),
        createdAt: new Date(Date.now() - (15 - i) * 60000),
        expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
      });
    }
    // Add target token
    tokens.push({
      _id: 'token-target',
      tokenHash: legacyHash,
      createdAt: new Date(Date.now() - 60000),
      expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
    });

    const fakeUser: any = {
      _id: 'user-h7-1',
      username: 'testpilot',
      refreshTokens: tokens,
      save: vi.fn().mockResolvedValue(undefined),
    };

    vi.spyOn(User, 'findById').mockResolvedValue(fakeUser as any);

    const result = await authService.refreshAccessToken('user-h7-1', rawTargetToken);
    expect(result).toBeDefined();
    expect(result.accessToken).toBeDefined();
    expect(result.refreshToken).toBeDefined();

    // Check that target token was replaced and new token has sha256: prefix
    const newToken = fakeUser.refreshTokens.find((t: any) => t.tokenHash.startsWith('sha256:'));
    expect(newToken).toBeDefined();
    expect(fakeUser.refreshTokens.some((t: any) => t._id === 'token-target')).toBe(false);
  });

  it('refresh with sha256 token does not call bcrypt', async () => {
    const bcryptSpy = vi.spyOn(bcrypt, 'compare');
    const rawToken = 'sha256-test-token-abcdef';

    // Hash with sha256
    const crypto = await import('crypto');
    const shaHash = `sha256:${crypto.createHash('sha256').update(rawToken).digest('hex')}`;

    const fakeUser: any = {
      _id: 'user-h7-2',
      username: 'fastpilot',
      refreshTokens: [
        {
          _id: 'token-sha',
          tokenHash: shaHash,
          createdAt: new Date(),
          expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
        },
      ],
      save: vi.fn().mockResolvedValue(undefined),
    };

    vi.spyOn(User, 'findById').mockResolvedValue(fakeUser as any);

    const result = await authService.refreshAccessToken('user-h7-2', rawToken);
    expect(result).toBeDefined();
    // Bcrypt must NEVER be called when sha256 token matches
    expect(bcryptSpy).not.toHaveBeenCalled();
  });

  it('cap keeps 10 tokens when new token is issued', async () => {
    const tokens: any[] = [];
    for (let i = 0; i < 15; i++) {
      tokens.push({
        _id: `token-old-${i}`,
        tokenHash: `sha256:dummyhash${i}`,
        createdAt: new Date(Date.now() - (20 - i) * 60000),
        expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
      });
    }

    const rawMatchToken = 'sha256-match-token';
    const crypto = await import('crypto');
    const matchHash = `sha256:${crypto.createHash('sha256').update(rawMatchToken).digest('hex')}`;
    tokens.push({
      _id: 'token-to-match',
      tokenHash: matchHash,
      createdAt: new Date(),
      expiresAt: new Date(Date.now() + 7 * 24 * 3600 * 1000),
    });

    const fakeUser: any = {
      _id: 'user-h7-3',
      username: 'cappilot',
      refreshTokens: tokens,
      save: vi.fn().mockResolvedValue(undefined),
    };

    vi.spyOn(User, 'findById').mockResolvedValue(fakeUser as any);

    await authService.refreshAccessToken('user-h7-3', rawMatchToken);

    // Stored tokens must be capped at 10
    expect(fakeUser.refreshTokens.length).toBeLessThanOrEqual(10);
  });

  it('limiter fail-open: a limiter whose store never resolves lets the request through after timeout and next() is called exactly once', async () => {
    // Create a lazy limiter with a store whose sendCommand never resolves
    const hangingLimiter = createLazyLimiter({
      windowMs: 60000,
      limit: 10,
      keyPrefix: 'test-hanging',
      enableInTest: true,
      customStore: {
        init: () => {},
        increment: () => new Promise(() => {}), // never resolves
        decrement: () => {},
        resetKey: () => {},
      } as any,
    });

    const req: any = { path: '/api/test', originalUrl: '/api/test', ip: '127.0.0.1', headers: {}, app: { get: () => false } };
    const res: any = {};
    let nextCalls = 0;

    await new Promise<void>((resolve) => {
      const next = () => {
        nextCalls++;
        if (nextCalls === 1) {
          // Wait slightly to verify next is not called again
          setTimeout(resolve, 100);
        }
      };

      hangingLimiter(req, res, next);
    });

    expect(nextCalls).toBe(1);
    expect(req.limiterWaitMs).toBeGreaterThanOrEqual(700);
  });
});

