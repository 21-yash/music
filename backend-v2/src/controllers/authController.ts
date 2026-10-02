import { Request, Response, NextFunction } from 'express';
import * as authService from '../services/authService';
import { sendSuccess } from '../utils/response';

/**
 * Auth controller.
 *
 * Controllers are thin — they extract input from the request, call the
 * service, and format the response. No business logic here.
 */

/** POST /api/v1/auth/register */
export async function register(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { username, email, password } = req.body;
    const deviceName = req.body.deviceName as string | undefined;

    const result = await authService.register(
      { username, email, password },
      deviceName,
    );

    sendSuccess(res, result, 201, 'Registration successful');
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/login */
export async function login(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { login: loginId, password, deviceName } = req.body;

    const result = await authService.login({
      login: loginId,
      password,
      deviceName,
    });

    sendSuccess(res, result, 200, 'Login successful');
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/refresh */
export async function refresh(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { refreshToken, userId, deviceName } = req.body;

    const tokens = await authService.refreshAccessToken(
      userId,
      refreshToken,
      deviceName,
    );

    sendSuccess(res, tokens);
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/logout */
export async function logout(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { refreshToken } = req.body;
    const userId = req.user!.userId;

    await authService.logout(userId, refreshToken);

    sendSuccess(res, null, 200, 'Logged out successfully');
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/logout-all */
export async function logoutAll(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    await authService.logoutAll(userId);

    sendSuccess(res, null, 200, 'Logged out from all devices');
  } catch (error) {
    next(error);
  }
}

/** GET /api/v1/auth/me */
export async function getMe(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const user = await authService.getCurrentUser(userId);

    sendSuccess(res, user);
  } catch (error) {
    next(error);
  }
}

/** GET /api/v1/auth/sessions */
export async function getSessions(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const currentRefreshToken = req.query.currentToken as string | undefined;

    const sessions = await authService.getSessions(userId, currentRefreshToken);

    sendSuccess(res, sessions);
  } catch (error) {
    next(error);
  }
}

/** DELETE /api/v1/auth/sessions/:sessionId */
export async function revokeSession(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const sessionId = req.params.sessionId as string;

    await authService.revokeSession(userId, sessionId);

    sendSuccess(res, null, 200, 'Session revoked');
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/change-password */
export async function changePassword(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { currentPassword, newPassword } = req.body;

    await authService.changePassword(userId, currentPassword, newPassword);

    sendSuccess(res, null, 200, 'Password changed successfully');
  } catch (error) {
    next(error);
  }
}

/** POST /api/v1/auth/delete-account */
export async function deleteAccount(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { password } = req.body;

    await authService.deleteAccount(userId, password);

    sendSuccess(res, null, 200, 'Account deleted');
  } catch (error) {
    next(error);
  }
}
