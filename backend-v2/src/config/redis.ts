import Redis from 'ioredis';
import { env } from './env';
import { logger } from '../utils/logger';

let redisClient: Redis | null = null;

/**
 * Create and return the singleton Redis client.
 *
 * Uses ioredis, which supports automatic reconnection out of the box.
 * On initial connection failure the promise rejects so the caller can
 * decide whether to abort startup or continue without Redis.
 */
export function createRedisClient(): Redis {
  if (redisClient) return redisClient;

  redisClient = new Redis(env.REDIS_URL, {
    maxRetriesPerRequest: 3,
    retryStrategy(times: number) {
      // Exponential backoff capped at 10 seconds
      const delay = Math.min(times * 200, 10_000);
      logger.warn({ attempt: times, delayMs: delay }, 'Redis reconnecting');
      return delay;
    },
    lazyConnect: true, // Don't connect until we explicitly call .connect()
  });

  redisClient.on('connect', () => {
    logger.info('Redis connected');
  });

  redisClient.on('error', (error) => {
    logger.error({ error }, 'Redis connection error');
  });

  redisClient.on('close', () => {
    logger.warn('Redis connection closed');
  });

  return redisClient;
}

/**
 * Connect the Redis client. Call this during startup.
 */
export async function connectRedis(): Promise<void> {
  const client = createRedisClient();
  try {
    await client.connect();
  } catch (error) {
    logger.error({ error }, 'Failed to connect to Redis');
    throw error;
  }
}

/**
 * Gracefully close the Redis connection.
 */
export async function disconnectRedis(): Promise<void> {
  if (!redisClient) return;
  try {
    await redisClient.quit();
    redisClient = null;
    logger.info('Redis connection closed');
  } catch (error) {
    logger.error({ error }, 'Error closing Redis connection');
  }
}

/**
 * Get the current Redis client. Throws if not yet initialized.
 */
export function getRedisClient(): Redis {
  if (!redisClient) {
    throw new Error(
      'Redis client not initialized. Call createRedisClient() first.',
    );
  }
  return redisClient;
}
