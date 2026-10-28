import { Router } from 'express';
import * as authController from '../controllers/authController';
import { validate } from '../middleware/validate';
import { authenticate } from '../middleware/authenticate';
import {
  registerSchema,
  loginSchema,
  refreshTokenSchema,
  logoutSchema,
  changePasswordSchema,
  deleteAccountSchema,
} from './schemas/authSchemas';
import {
  loginLimiter,
  loginIpLimiter,
  registerLimiter,
  refreshLimiter,
} from '../middleware/rateLimit';

const router = Router();

// ─── Public routes (no auth required) ─────────────────────────────────

/** Register a new user */
router.post(
  '/register',
  registerLimiter,
  validate({ body: registerSchema }),
  authController.register,
);

/** Login with username/email + password */
router.post(
  '/login',
  loginIpLimiter,
  loginLimiter,
  validate({ body: loginSchema }),
  authController.login,
);

/** Refresh access token (no auth header needed — access token is expired) */
router.post(
  '/refresh',
  refreshLimiter,
  validate({ body: refreshTokenSchema }),
  authController.refresh,
);

// ─── Protected routes (auth required) ─────────────────────────────────

/** Get current user profile */
router.get('/me', authenticate, authController.getMe);

/** Logout current session */
router.post(
  '/logout',
  authenticate,
  validate({ body: logoutSchema }),
  authController.logout,
);

/** Logout from all devices */
router.post('/logout-all', authenticate, authController.logoutAll);

/** Get active sessions */
router.get('/sessions', authenticate, authController.getSessions);

/** Revoke a specific session */
router.delete(
  '/sessions/:sessionId',
  authenticate,
  authController.revokeSession,
);

/** Change password */
router.post(
  '/change-password',
  authenticate,
  validate({ body: changePasswordSchema }),
  authController.changePassword,
);

/** Delete account */
router.post(
  '/delete-account',
  authenticate,
  validate({ body: deleteAccountSchema }),
  authController.deleteAccount,
);

export default router;
