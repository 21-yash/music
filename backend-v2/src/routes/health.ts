import { Router } from 'express';
import mongoose from 'mongoose';
import { sendSuccess } from '../utils/response';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';

const router = Router();

/**
 * GET /api/v1/health
 *
 * Returns current health status of the application and its dependencies.
 * Used by monitoring, load balancers, and container orchestrators.
 */
router.get('/health', async (_req, res) => {
  const mongoStatus = mongoose.connection.readyState === 1 ? 'connected' : 'disconnected';

  let redisStatus: string;
  try {
    const redis = getRedisClient();
    await redis.ping();
    redisStatus = 'connected';
  } catch {
    redisStatus = 'disconnected';
    logger.warn('Health check: Redis is disconnected');
  }

  const data = {
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    environment: process.env.NODE_ENV ?? 'unknown',
    dependencies: {
      mongodb: mongoStatus,
      redis: redisStatus,
    },
  };

  sendSuccess(res, data);
});

export default router;
