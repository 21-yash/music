import mongoose from 'mongoose';
import { env } from './env';
import { logger } from '../utils/logger';

/**
 * Connect to MongoDB.
 *
 * Handles initial connection and attaches event listeners for runtime
 * connection issues. Does NOT call process.exit on failure — the caller
 * (server.ts) decides how to handle a failed connection.
 */
export async function connectMongoDB(): Promise<void> {
  try {
    await mongoose.connect(env.MONGODB_URI);
    logger.info(
      { database: mongoose.connection.name },
      'Connected to MongoDB',
    );
  } catch (error) {
    logger.error({ error }, 'Failed to connect to MongoDB');
    throw error;
  }

  mongoose.connection.on('error', (error) => {
    logger.error({ error }, 'MongoDB connection error');
  });

  mongoose.connection.on('disconnected', () => {
    logger.warn('MongoDB disconnected');
  });

  mongoose.connection.on('reconnected', () => {
    logger.info('MongoDB reconnected');
  });
}

/**
 * Gracefully close the MongoDB connection.
 */
export async function disconnectMongoDB(): Promise<void> {
  try {
    await mongoose.disconnect();
    logger.info('MongoDB connection closed');
  } catch (error) {
    logger.error({ error }, 'Error closing MongoDB connection');
  }
}
