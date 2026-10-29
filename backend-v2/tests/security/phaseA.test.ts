import { describe, it, expect, vi, beforeAll, afterAll, beforeEach } from 'vitest';
import http from 'http';
import request from 'supertest';
import { io as ioClient, Socket as ClientSocket } from 'socket.io-client';
import { createApp } from '../../src/app';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';
import * as musicService from '../../src/services/musicService';
import { Invite } from '../../src/models/Invite';
import { User } from '../../src/models/User';
import { initializeSocketIO } from '../../src/websocket';
import { hashInviteCode } from '../../src/utils/invite';
import * as redisConfig from '../../src/config/redis';

describe('Phase A Security Tests', () => {
  const VALID_KEY = env.APP_API_KEY;
  const VALID_TOKEN = generateAccessToken({
    userId: 'user-123456789012345678901234',
    username: 'testpilot',
  });

  // ─── 1. Route Security & API Key Validation ─────────────────────────
  describe('Music routes authentication & API key', () => {
    const app = createApp();

    it('/music/trending: no key -> 403', async () => {
      const res = await request(app).get('/api/v1/music/trending');
      expect(res.status).toBe(403);
      expect(res.body.error?.code).toBe('FORBIDDEN');
    });

    it('/music/trending: bad key -> 403', async () => {
      const res = await request(app)
        .get('/api/v1/music/trending')
        .set('X-Api-Key', 'invalid-api-key-that-does-not-match-at-all');
      expect(res.status).toBe(403);
      expect(res.body.error?.code).toBe('FORBIDDEN');
    });

    it('/music/trending: key only (no JWT) -> 401', async () => {
      const res = await request(app)
        .get('/api/v1/music/trending')
        .set('X-Api-Key', VALID_KEY);
      expect(res.status).toBe(401);
      expect(res.body.error?.code).toBe('UNAUTHORIZED');
    });

    it('/music/trending: key + JWT -> 200', async () => {
      vi.spyOn(musicService, 'getTrending').mockResolvedValueOnce([] as any);

      const res = await request(app)
        .get('/api/v1/music/trending')
        .set('X-Api-Key', VALID_KEY)
        .set('Authorization', `Bearer ${VALID_TOKEN}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
    });

    it('GET /music/stream?ref=<id> with key + JWT -> 200', async () => {
      vi.spyOn(musicService, 'resolveStreamUrl').mockResolvedValueOnce({
        type: 'stream',
        url: 'https://cdn.example.com/audio.mp3',
        quality: 'high',
        format: 'mp3',
        bitrate: 320,
      } as any);

      const res = await request(app)
        .get('/api/v1/music/stream?ref=song-id-123')
        .set('X-Api-Key', VALID_KEY)
        .set('Authorization', `Bearer ${VALID_TOKEN}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data?.url).toBe('https://cdn.example.com/audio.mp3');
    });
  });

  // ─── 2. Single-use Invite Codes ─────────────────────────────────────
  describe('Single-use invite codes registration', () => {
    const app = createApp();
    let inviteStore: Map<string, {
      codeHash: string;
      createdAt: Date;
      expiresAt: Date | null;
      usedAt: Date | null;
      usedBy: any | null;
    }>;
    let userStore: Map<string, any>;

    beforeEach(() => {
      inviteStore = new Map();
      userStore = new Map();

      // Mock Invite.findOneAndUpdate to simulate atomic database claim
      vi.spyOn(Invite, 'findOneAndUpdate').mockImplementation(async (query: any, update: any) => {
        const invite = inviteStore.get(query.codeHash);
        if (!invite) return null;
        if (query.usedAt !== null && invite.usedAt !== query.usedAt) return null;
        if (invite.usedAt !== null) return null;

        const now = new Date();
        if (invite.expiresAt && invite.expiresAt <= now) return null;

        if (update.$set?.usedAt) {
          invite.usedAt = update.$set.usedAt;
        }
        return {
          _id: 'invite-id-123',
          ...invite,
        } as any;
      });

      // Mock Invite.updateOne to simulate releasing or binding invite
      vi.spyOn(Invite, 'updateOne').mockImplementation(async (query: any, update: any) => {
        for (const invite of inviteStore.values()) {
          if (update.$set?.usedAt === null) {
            invite.usedAt = null;
          }
          if (update.$set?.usedBy) {
            invite.usedBy = update.$set.usedBy;
          }
        }
        return { acknowledged: true } as any;
      });

      // Mock User.findOne to simulate checking duplicate user
      vi.spyOn(User, 'findOne').mockImplementation(async (query: any) => {
        const orConditions = query.$or || [];
        for (const user of userStore.values()) {
          for (const cond of orConditions) {
            if (cond.email && user.email === cond.email) return user as any;
            if (cond.username && user.username === cond.username) return user as any;
          }
        }
        return null;
      });

      // Mock User.create to simulate user registration
      vi.spyOn(User, 'create').mockImplementation(async (doc: any) => {
        const user = {
          _id: 'user-id-' + Math.random().toString(36).slice(2),
          username: doc.username,
          email: doc.email,
          password: doc.password,
          refreshTokens: [],
          lastLogin: null,
          createdAt: new Date(),
          updatedAt: new Date(),
          comparePassword: vi.fn(),
          save: vi.fn().mockResolvedValue(true),
        };
        userStore.set(doc.username, user);
        return user as any;
      });
    });

    it('missing invite code -> 422 (validation error)', async () => {
      const res = await request(app).post('/api/v1/auth/register').send({
        username: 'newuser1',
        email: 'newuser1@example.com',
        password: 'password123',
      });
      expect(res.status).toBe(422);
    });

    it('wrong invite code -> 403 (Forbidden)', async () => {
      const res = await request(app).post('/api/v1/auth/register').send({
        username: 'newuser2',
        email: 'newuser2@example.com',
        password: 'password123',
        inviteCode: 'WRONG-INVITE-CODE',
      });
      expect(res.status).toBe(403);
      expect(res.body.error?.message).toContain('invite code');
    });

    it('expired invite code -> 403', async () => {
      const rawCode = 'EXPIRED-CODE-001';
      const codeHash = hashInviteCode(rawCode);
      inviteStore.set(codeHash, {
        codeHash,
        createdAt: new Date(Date.now() - 100000),
        expiresAt: new Date(Date.now() - 1000), // Expired
        usedAt: null,
        usedBy: null,
      });

      const res = await request(app).post('/api/v1/auth/register').send({
        username: 'newuser3',
        email: 'newuser3@example.com',
        password: 'password123',
        inviteCode: rawCode,
      });
      expect(res.status).toBe(403);
    });

    it('valid code -> 201; and the same code used again -> 403', async () => {
      const rawCode = 'VALID-CODE-9999';
      const codeHash = hashInviteCode(rawCode);
      inviteStore.set(codeHash, {
        codeHash,
        createdAt: new Date(),
        expiresAt: null,
        usedAt: null,
        usedBy: null,
      });

      // 1st attempt: success
      const res1 = await request(app).post('/api/v1/auth/register').send({
        username: 'firstuser',
        email: 'first@example.com',
        password: 'password123',
        inviteCode: rawCode,
      });
      expect(res1.status).toBe(201);
      expect(res1.body.success).toBe(true);

      // 2nd attempt with same code: rejected
      const res2 = await request(app).post('/api/v1/auth/register').send({
        username: 'seconduser',
        email: 'second@example.com',
        password: 'password123',
        inviteCode: rawCode,
      });
      expect(res2.status).toBe(403);
    });

    it('failed registration (duplicate username) leaves the code usable', async () => {
      const rawCode = 'REUSABLE-CODE-1';
      const codeHash = hashInviteCode(rawCode);
      inviteStore.set(codeHash, {
        codeHash,
        createdAt: new Date(),
        expiresAt: null,
        usedAt: null,
        usedBy: null,
      });

      // Seed existing user
      userStore.set('existinguser', {
        username: 'existinguser',
        email: 'existing@example.com',
      });

      // Try registering with duplicate username
      const resFailed = await request(app).post('/api/v1/auth/register').send({
        username: 'existinguser',
        email: 'different@example.com',
        password: 'password123',
        inviteCode: rawCode,
      });
      expect(resFailed.status).toBe(409);

      // The invite code must have been released and remain usable
      const resSuccess = await request(app).post('/api/v1/auth/register').send({
        username: 'freshuser',
        email: 'fresh@example.com',
        password: 'password123',
        inviteCode: rawCode,
      });
      expect(resSuccess.status).toBe(201);
    });

    it('two concurrent registrations with one code -> exactly one succeeds', async () => {
      const rawCode = 'CONCURRENT-CD-1';
      const codeHash = hashInviteCode(rawCode);
      inviteStore.set(codeHash, {
        codeHash,
        createdAt: new Date(),
        expiresAt: null,
        usedAt: null,
        usedBy: null,
      });

      const [resA, resB] = await Promise.all([
        request(app).post('/api/v1/auth/register').send({
          username: 'userA',
          email: 'userA@example.com',
          password: 'password123',
          inviteCode: rawCode,
        }),
        request(app).post('/api/v1/auth/register').send({
          username: 'userB',
          email: 'userB@example.com',
          password: 'password123',
          inviteCode: rawCode,
        }),
      ]);

      const statuses = [resA.status, resB.status];
      expect(statuses).toContain(201);
      expect(statuses).toContain(403);
    });
  });

  // ─── 3. Socket.IO Handshake Auth & Size Limit ────────────────────────
  describe('Socket.IO handshake authentication', () => {
    let server: http.Server;
    let port: number;

    beforeAll(async () => {
      const app = createApp();
      server = http.createServer(app);
      initializeSocketIO(server);

      await new Promise<void>((resolve) => {
        server.listen(0, () => {
          const addr = server.address() as any;
          port = addr.port;
          resolve();
        });
      });
    });

    afterAll(async () => {
      await new Promise<void>((resolve) => {
        server.close(() => resolve());
      });
    });

    it('Socket with bad API key is rejected', async () => {
      const socket = ioClient(`http://localhost:${port}`, {
        auth: { apiKey: 'invalid-key-that-is-way-too-short-or-wrong', token: VALID_TOKEN },
        transports: ['websocket'],
        reconnection: false,
      });

      const errMessage = await new Promise<string>((resolve) => {
        socket.on('connect_error', (err) => resolve(err.message));
        socket.on('connect', () => resolve('connected'));
      });
      socket.disconnect();

      expect(errMessage).toContain('Invalid or missing API key');
    });

    it('Socket with missing token is rejected', async () => {
      const socket = ioClient(`http://localhost:${port}`, {
        auth: { apiKey: VALID_KEY },
        transports: ['websocket'],
        reconnection: false,
      });

      const errMessage = await new Promise<string>((resolve) => {
        socket.on('connect_error', (err) => resolve(err.message));
        socket.on('connect', () => resolve('connected'));
      });
      socket.disconnect();

      expect(errMessage).toContain('Authentication token required');
    });

    it('Socket with valid key and token connects successfully and ignores spoofed identity in jam2:create', async () => {
      const socket = ioClient(`http://localhost:${port}`, {
        auth: { apiKey: VALID_KEY, token: VALID_TOKEN },
        transports: ['websocket'],
        reconnection: false,
      });

      const connected = await new Promise<boolean>((resolve) => {
        socket.on('connect', () => resolve(true));
        socket.on('connect_error', () => resolve(false));
      });
      expect(connected).toBe(true);

      // Verify that jam2:create uses socket.data.user (testpilot) and ignores payload spoofing
      const ack: any = await new Promise((resolve) => {
        socket.emit(
          'jam2:create',
          {
            protocol: 2,
            userId: 'hacker-impersonated-id',
            username: 'hacker-impersonated-name',
          },
          resolve,
        );
      });

      expect(ack.ok).toBe(true);
      expect(ack.room.hostUserId).toBe('user-123456789012345678901234');
      const hostMember = ack.room.members.find(
        (m) => m.userId === 'user-123456789012345678901234',
      );
      expect(hostMember?.username).toBe('testpilot');

      socket.disconnect();
    });
  });

  // ─── 4. Rate Limiting & Proxy Trust ──────────────────────────────────
  describe('Rate Limiting & Proxy Trust', () => {
    it('app trust proxy is configured to 1', () => {
      const app = createApp();
      expect(app.get('trust proxy')).toBe(1);
    });

    it('varying prepended X-Forwarded-For does not change req.ip when trust proxy is 1', async () => {
      const express = (await import('express')).default;
      const app = express();
      app.set('trust proxy', 1);

      let capturedIpA: string | undefined;
      let capturedIpB: string | undefined;

      app.get('/test-proxy-1', (req, res) => {
        capturedIpA = req.ip;
        res.sendStatus(200);
      });

      app.get('/test-proxy-2', (req, res) => {
        capturedIpB = req.ip;
        res.sendStatus(200);
      });

      // Attacker attempts spoofing by prepending random IPs:
      // Proxy appends real client IP 198.51.100.25 at the end
      await request(app)
        .get('/test-proxy-1')
        .set('X-Forwarded-For', '10.0.0.1, 198.51.100.25');

      await request(app)
        .get('/test-proxy-2')
        .set('X-Forwarded-For', '203.0.113.88, 198.51.100.25');

      // Both should resolve to the 1 hop upstream client IP (198.51.100.25)
      expect(capturedIpA).toBe('198.51.100.25');
      expect(capturedIpB).toBe('198.51.100.25');
      expect(capturedIpA).toBe(capturedIpB);
    });

    it('11 wrong logins from one IP triggers rate limit (429)', async () => {
      const app = createApp();
      const hits = new Map<string, number>();

      // Mock Redis client to simulate atomic increment in RedisStore
      const mockRedis = {
        call: vi.fn().mockImplementation(async (cmd: string, ...args: string[]) => {
          const lowerCmd = cmd.toLowerCase();
          if (lowerCmd === 'script') {
            return 'mock-redis-script-sha';
          }
          if (lowerCmd === 'eval' || lowerCmd === 'evalsha') {
            // rate-limit-redis evalsha / eval
            const key = args[2] || 'key';
            const count = (hits.get(key) || 0) + 1;
            hits.set(key, count);
            return [count, 900000];
          }
          if (lowerCmd === 'decr') {
            const key = args[0] || 'key';
            const count = Math.max(0, (hits.get(key) || 0) - 1);
            hits.set(key, count);
            return count;
          }
          return null;
        }),
      };
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue(mockRedis as any);

      // Temporarily enable production mode to activate rate limiting
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      try {
        let lastStatus = 0;
        for (let i = 1; i <= 11; i++) {
          const res = await request(app)
            .post('/api/v1/auth/login')
            .send({ login: 'victim@example.com', password: 'wrongpassword' });
          lastStatus = res.status;
        }

        // On the 11th wrong login, rate limit must be reached (429)
        expect(lastStatus).toBe(429);
      } finally {
        process.env.NODE_ENV = originalEnv;
      }
    });

    it('Server boots with Redis unavailable and limited route does not crash (fails open to 401)', async () => {
      const app = createApp();
      vi.spyOn(User, 'findOne').mockResolvedValue(null);

      // Mock Redis client call to reject (simulating connection refused/down)
      const mockFailingRedis = {
        call: vi.fn().mockRejectedValue(new Error('Connection refused to redis:6379')),
      };
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue(mockFailingRedis as any);

      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      try {
        const res = await request(app)
          .post('/api/v1/auth/login')
          .send({ login: 'victim@example.com', password: 'wrongpassword' });
        // Traffic passes through to authController, returning 401 Unauthorized instead of 500 Internal Server Error
        expect(res.status).toBe(401);
        expect(res.body.error?.code).toBe('UNAUTHORIZED');
      } finally {
        process.env.NODE_ENV = originalEnv;
      }
    });

    it('Redis recovery: limits start counting again after Redis reconnects (11th wrong login -> 429)', async () => {
      const app = createApp();
      vi.spyOn(User, 'findOne').mockResolvedValue(null);

      let redisIsDown = true;
      const hits = new Map<string, number>();

      const mockRecoveringRedis = {
        call: vi.fn().mockImplementation(async (cmd: string, ...args: string[]) => {
          if (redisIsDown) {
            throw new Error('Connection refused to redis:6379');
          }
          const lowerCmd = cmd.toLowerCase();
          if (lowerCmd === 'script') {
            return 'mock-script-sha';
          }
          if (lowerCmd === 'eval' || lowerCmd === 'evalsha') {
            const key = args[2] || 'key';
            const count = (hits.get(key) || 0) + 1;
            hits.set(key, count);
            return [count, 900000];
          }
          if (lowerCmd === 'decr') {
            const key = args[0] || 'key';
            const count = Math.max(0, (hits.get(key) || 0) - 1);
            hits.set(key, count);
            return count;
          }
          return null;
        }),
      };
      vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue(mockRecoveringRedis as any);

      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      try {
        // 1. Initial request when Redis is down: fails open, returns 401
        const resDown = await request(app)
          .post('/api/v1/auth/login')
          .send({ login: 'victim@example.com', password: 'wrongpassword' });
        expect(resDown.status).toBe(401);

        // 2. Redis comes back up
        redisIsDown = false;

        // 3. Perform 10 wrong logins (should be allowed through, returning 401)
        for (let i = 1; i <= 10; i++) {
          const res = await request(app)
            .post('/api/v1/auth/login')
            .send({ login: 'victim@example.com', password: 'wrongpassword' });
          expect(res.status).toBe(401);
        }

        // 4. 11th wrong login must trigger rate limit (429)
        const res11 = await request(app)
          .post('/api/v1/auth/login')
          .send({ login: 'victim@example.com', password: 'wrongpassword' });
        expect(res11.status).toBe(429);
      } finally {
        process.env.NODE_ENV = originalEnv;
      }
    });
  });
});
