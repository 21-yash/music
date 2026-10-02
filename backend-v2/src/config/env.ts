import { z } from 'zod';
import dotenv from 'dotenv';
import path from 'path';

// Load .env before validation
dotenv.config({ path: path.resolve(process.cwd(), '.env') });

/**
 * Environment variable schema.
 *
 * Only variables required by the current implementation are listed here.
 * Do not add variables for features that don't exist yet (see Claude.md).
 */
const envSchema = z.object({
  NODE_ENV: z
    .enum(['development', 'production', 'test'])
    .default('development'),
  PORT: z.coerce.number().int().positive().default(3000),

  // MongoDB
  MONGODB_URI: z.string().min(1, 'MONGODB_URI is required'),

  // Redis
  REDIS_URL: z.string().min(1, 'REDIS_URL is required'),

  // CORS — comma-separated list of allowed origins
  CORS_ORIGINS: z.string().min(1, 'CORS_ORIGINS is required'),

  // JWT secrets — required even at startup so the app fails fast if missing
  JWT_ACCESS_SECRET: z.string().min(1, 'JWT_ACCESS_SECRET is required'),
  JWT_REFRESH_SECRET: z.string().min(1, 'JWT_REFRESH_SECRET is required'),
});

export type Env = z.infer<typeof envSchema>;

/**
 * Parse and validate environment variables at startup.
 * Throws immediately with a clear message if anything is missing or invalid,
 * ensuring we never run with bad configuration.
 */
function validateEnv(): Env {
  const result = envSchema.safeParse(process.env);

  if (!result.success) {
    const formatted = result.error.issues
      .map((issue) => `  • ${issue.path.join('.')}: ${issue.message}`)
      .join('\n');

    // eslint-disable-next-line no-console
    console.error(
      `\n❌ Invalid environment variables:\n${formatted}\n\nSee .env.example for reference.\n`,
    );
    process.exit(1);
  }

  return result.data;
}

export const env = validateEnv();
