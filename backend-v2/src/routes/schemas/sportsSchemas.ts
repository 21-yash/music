import { z } from 'zod';

/**
 * Zod schemas for sports endpoint validation.
 */

export const matchesSchema = {
  query: z.object({
    filter: z.enum(['live', 'upcoming', 'recent'], {
      required_error: 'Filter is required (live, upcoming, or recent)',
    }),
  }),
};

export const matchIdSchema = {
  params: z.object({
    matchId: z.string().min(1, 'Match ID is required'),
  }),
};
