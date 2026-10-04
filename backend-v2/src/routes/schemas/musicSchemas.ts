import { z } from 'zod';

export const searchSchema = z.object({
  query: z.object({
    q: z.string().min(1, 'Query is required'),
    page: z.coerce.number().int().positive().default(1),
    limit: z.coerce.number().int().positive().max(50).default(20),
  }),
});

export const streamSchema = z.object({
  query: z.object({
    id: z.string().min(1, 'Song ID is required'),
    quality: z.enum(['high', 'medium', 'low']).default('high'),
  }),
});

export const idParamSchema = z.object({
  id: z.string().min(1, 'ID is required'),
});
