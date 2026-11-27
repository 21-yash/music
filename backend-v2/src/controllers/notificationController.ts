import { Request, Response, NextFunction } from 'express';
import * as notificationService from '../services/notificationService';
import { sportsNotifier } from '../jobs/sportsNotifier';
import { sendSuccess } from '../utils/response';

export async function getPreferences(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const preferences = await notificationService.getPreferences(req.user!.userId);
    sendSuccess(res, { preferences });
  } catch (error) {
    next(error);
  }
}

export async function updatePreferences(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const preferences = await notificationService.updatePreferences(
      req.user!.userId,
      req.body,
    );
    if (preferences.enabled) {
      void sportsNotifier.requestDiscoverySweep();
    }
    sendSuccess(res, { preferences });
  } catch (error) {
    next(error);
  }
}

export async function registerToken(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const result = await notificationService.registerFcmToken(
      req.user!.userId,
      req.body.token,
      req.body.platform,
    );
    void sportsNotifier.requestDiscoverySweep();
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function unregisterToken(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const result = await notificationService.unregisterFcmToken(
      req.user!.userId,
      req.body.token,
    );
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}
