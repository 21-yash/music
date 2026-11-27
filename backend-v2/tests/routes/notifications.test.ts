import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Request, Response, NextFunction } from 'express';
import {
  registerFcmTokenSchema,
  unregisterFcmTokenSchema,
  updateNotificationPreferencesSchema,
} from '../../src/routes/schemas/notificationSchemas';
import * as notificationController from '../../src/controllers/notificationController';
import * as notificationService from '../../src/services/notificationService';

vi.mock('../../src/services/notificationService', () => ({
  getPreferences: vi.fn(),
  updatePreferences: vi.fn(),
  registerFcmToken: vi.fn(),
  unregisterFcmToken: vi.fn(),
}));

describe('notificationSchemas', () => {
  it('validates registerFcmTokenSchema', () => {
    expect(
      registerFcmTokenSchema.safeParse({ token: 'abc1234567890', platform: 'android' }).success,
    ).toBe(true);
    expect(registerFcmTokenSchema.safeParse({ token: 'short' }).success).toBe(false);
    expect(
      registerFcmTokenSchema.safeParse({ token: 'abc1234567890', platform: 'ios' }).success,
    ).toBe(false);
  });

  it('validates unregisterFcmTokenSchema', () => {
    expect(unregisterFcmTokenSchema.safeParse({ token: 'abc1234567890' }).success).toBe(true);
    expect(unregisterFcmTokenSchema.safeParse({ token: '' }).success).toBe(false);
  });

  it('validates updateNotificationPreferencesSchema', () => {
    expect(
      updateNotificationPreferencesSchema.safeParse({ milestones: false, matchStarted: true }).success,
    ).toBe(true);
    expect(updateNotificationPreferencesSchema.safeParse({}).success).toBe(false);
    expect(
      updateNotificationPreferencesSchema.safeParse({ milestones: 'yes' }).success,
    ).toBe(false);
  });
});

describe('notificationController', () => {
  let req: Partial<Request>;
  let res: Partial<Response>;
  let next: NextFunction;

  beforeEach(() => {
    vi.clearAllMocks();
    req = {
      user: { userId: '507f1f77bcf86cd799439011', email: 'test@example.com' } as any,
      body: {},
    };
    res = {
      status: vi.fn().mockReturnThis(),
      json: vi.fn().mockReturnThis(),
    };
    next = vi.fn();
  });

  it('getPreferences returns user notification preferences', async () => {
    const prefs = {
      enabled: true,
      matchStartingSoon: true,
      matchStarted: true,
      matchConcluded: true,
      milestones: true,
      matchUpdates: true,
    };
    vi.mocked(notificationService.getPreferences).mockResolvedValue(prefs);

    await notificationController.getPreferences(req as Request, res as Response, next);

    expect(notificationService.getPreferences).toHaveBeenCalledWith('507f1f77bcf86cd799439011');
    expect(res.json).toHaveBeenCalledWith(
      expect.objectContaining({
        success: true,
        data: { preferences: prefs },
      }),
    );
  });

  it('updatePreferences updates only requested boolean flags', async () => {
    const updated = {
      enabled: true,
      matchStartingSoon: true,
      matchStarted: true,
      matchConcluded: true,
      milestones: false,
      matchUpdates: true,
    };
    req.body = { milestones: false };
    vi.mocked(notificationService.updatePreferences).mockResolvedValue(updated);

    await notificationController.updatePreferences(req as Request, res as Response, next);

    expect(notificationService.updatePreferences).toHaveBeenCalledWith(
      '507f1f77bcf86cd799439011',
      { milestones: false },
    );
    expect(res.json).toHaveBeenCalledWith(
      expect.objectContaining({
        success: true,
        data: { preferences: updated },
      }),
    );
  });

  it('registerToken and unregisterToken delegate cleanly', async () => {
    req.body = { token: 'token_abcdef_123456', platform: 'android' };
    vi.mocked(notificationService.registerFcmToken).mockResolvedValue({ registered: true });
    await notificationController.registerToken(req as Request, res as Response, next);
    expect(notificationService.registerFcmToken).toHaveBeenCalledWith(
      '507f1f77bcf86cd799439011',
      'token_abcdef_123456',
      'android',
    );

    vi.mocked(notificationService.unregisterFcmToken).mockResolvedValue({ removed: true });
    await notificationController.unregisterToken(req as Request, res as Response, next);
    expect(notificationService.unregisterFcmToken).toHaveBeenCalledWith(
      '507f1f77bcf86cd799439011',
      'token_abcdef_123456',
    );
  });
});
