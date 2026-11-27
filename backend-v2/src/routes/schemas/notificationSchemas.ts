import { z } from 'zod';

export const registerFcmTokenSchema = z.object({
  token: z.string().trim().min(10, 'Invalid FCM token').max(512),
  platform: z.enum(['android']).default('android'),
});

export const unregisterFcmTokenSchema = z.object({
  token: z.string().trim().min(10, 'Invalid FCM token').max(512),
});

export const updateNotificationPreferencesSchema = z
  .object({
    enabled: z.boolean().optional(),
    matchStartingSoon: z.boolean().optional(),
    matchStarted: z.boolean().optional(),
    matchConcluded: z.boolean().optional(),
    milestones: z.boolean().optional(),
    matchUpdates: z.boolean().optional(),
  })
  .refine((obj) => Object.keys(obj).length > 0, {
    message: 'At least one notification preference must be provided',
  });
