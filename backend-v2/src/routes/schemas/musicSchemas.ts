import { z } from 'zod';

export const searchSchema = z.object({
  query: z.object({
    q: z.string().min(1, 'Query is required'),
    provider: z.string().optional(),
    page: z.coerce.number().int().positive().default(1),
    limit: z.coerce.number().int().positive().max(50).default(20),
  }),
});

export const streamSchema = z.object({
  query: z.object({
    id: z.string().optional(),
    ref: z.string().optional(),
    provider: z.string().optional(),
    quality: z.enum(['high', 'medium', 'low']).default('high'),
  }).refine((data) => data.id || data.ref, {
    message: "Either 'id' or 'ref' must be provided",
  }),
});

export const idParamSchema = z.object({
  id: z.string().min(1, 'ID is required'),
});
