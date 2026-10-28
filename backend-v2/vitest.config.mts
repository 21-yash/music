import { defineConfig } from 'vitest/config';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    include: ['tests/**/*.test.ts'],
    alias: {
      '@config': path.resolve(__dirname, 'src/config'),
      '@controllers': path.resolve(__dirname, 'src/controllers'),
      '@routes': path.resolve(__dirname, 'src/routes'),
      '@models': path.resolve(__dirname, 'src/models'),
      '@services': path.resolve(__dirname, 'src/services'),
      '@providers': path.resolve(__dirname, 'src/providers'),
      '@websocket': path.resolve(__dirname, 'src/websocket'),
      '@jobs': path.resolve(__dirname, 'src/jobs'),
      '@middleware': path.resolve(__dirname, 'src/middleware'),
      '@utils': path.resolve(__dirname, 'src/utils'),
    },
    env: {
      NODE_ENV: 'test',
      MONGODB_URI: 'mongodb://localhost:27017/synq-test',
      REDIS_URL: 'redis://localhost:6379',
      CORS_ORIGINS: '*',
      JWT_ACCESS_SECRET: 'test-jwt-access-secret-for-vitest-environment',
      JWT_REFRESH_SECRET: 'test-jwt-refresh-secret-for-vitest-environment',
      APP_API_KEY: 'test-api-key-for-vitest-environment-must-be-min-32-chars-long',
    },
  },
});
