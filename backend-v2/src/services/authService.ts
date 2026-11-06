import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { User, IUser } from '../models/User';
import { Invite } from '../models/Invite';
import {
  generateAccessToken,
  generateRefreshToken,
  getRefreshTokenExpiry,
} from '../utils/tokens';
import {
  ConflictError,
  UnauthorizedError,
  NotFoundError,
  ForbiddenError,
} from '../utils/errors';
import { logger } from '../utils/logger';
import { hashInviteCode } from '../utils/invite';

// ─── Types ────────────────────────────────────────────────────────────

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
  inviteCode: string;
}

export interface LoginInput {
  login: string; // username or email
  password: string;
  deviceName?: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  lastLogin: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface SessionInfo {
  id: string;
  deviceName: string | null;
  createdAt: Date;
  expiresAt: Date;
  isCurrent: boolean;
}

// ─── Service ──────────────────────────────────────────────────────────

/**
 * Register a new user.
 *
 * Validates and atomically claims the single-use invite code before checking
 * for existing users (to prevent username/email enumeration). If registration fails,
 * the invite code is released so it remains usable.
 */
export async function register(
  input: RegisterInput,
  deviceName?: string,
): Promise<{ user: UserProfile; tokens: AuthTokens }> {
  // 1. Atomically claim invite code BEFORE any username/email lookup
  const codeHash = hashInviteCode(input.inviteCode);
  const now = new Date();

  const claimedInvite = await Invite.findOneAndUpdate(
    {
      codeHash,
      usedAt: null,
      $or: [{ expiresAt: null }, { expiresAt: { $gt: now } }],
    },
    { $set: { usedAt: now } },
    { new: true },
  );

  if (!claimedInvite) {
    throw new ForbiddenError('Invalid or already used invite code');
  }

  let inviteBound = false;

  try {
    // 2. Check for existing user by email or username
    const existing = await User.findOne({
      $or: [
        { email: input.email.toLowerCase() },
        { username: input.username },
      ],
    });

    if (existing) {
      if (existing.email === input.email.toLowerCase()) {
        throw new ConflictError('Email is already registered');
      }
      throw new ConflictError('Username is already taken');
    }

    // 3. Create user — password hashing happens in the pre-save hook
    const user = await User.create({
      username: input.username,
      email: input.email.toLowerCase(),
      password: input.password,
    });

    // 4. Mark invite as usedBy new user
    await Invite.updateOne(
      { _id: claimedInvite._id },
      { $set: { usedBy: user._id } },
    );
    inviteBound = true;

    // 5. Issue tokens
    const tokens = await issueTokens(user, deviceName ?? null);

    logger.info({ userId: user._id }, 'User registered');

    return {
      user: toUserProfile(user),
      tokens,
    };
  } catch (error) {
    // If registration fails before linking the user, release the invite back to usable
    if (!inviteBound) {
      await Invite.updateOne(
        { _id: claimedInvite._id, usedBy: null },
        { $set: { usedAt: null } },
      ).catch((releaseErr) => {
        logger.error({ releaseErr }, 'Failed to release claimed invite code');
      });
    }

    throw error;
  }
}

/**
 * Authenticate a user by username/email + password.
 */
export async function login(
  input: LoginInput,
): Promise<{ user: UserProfile; tokens: AuthTokens }> {
  // Find user by email or username
  const user = await User.findOne({
    $or: [
      { email: input.login.toLowerCase() },
      { username: input.login },
    ],
  });

  if (!user) {
    throw new UnauthorizedError('Invalid credentials');
  }

  const passwordMatch = await user.comparePassword(input.password);
  if (!passwordMatch) {
    throw new UnauthorizedError('Invalid credentials');
  }

  // Issue tokens
  const tokens = await issueTokens(user, input.deviceName ?? null);

  // Update last login
  user.lastLogin = new Date();
  await user.save();

  logger.info({ userId: user._id }, 'User logged in');

  return {
    user: toUserProfile(user),
    tokens,
  };
}

/**
 * Fast SHA-256 token hashing for refresh tokens.
 * Refresh tokens are 256-bit cryptographically secure random values from crypto.randomBytes,
 * so expensive key-derivation algorithms like bcrypt are unnecessary and block the Node event loop.
 */
function hashRefreshToken(rawToken: string): string {
  const digest = crypto.createHash('sha256').update(rawToken).digest('hex');
  return `sha256:${digest}`;
}

function verifyTokenHashSync(rawToken: string, storedHash: string): boolean {
  if (!storedHash.startsWith('sha256:')) return false;
  const digest = crypto.createHash('sha256').update(rawToken).digest('hex');
  const expected = `sha256:${digest}`;
  const a = Buffer.from(expected);
  const b = Buffer.from(storedHash);
  if (a.byteLength !== b.byteLength) return false;
  return crypto.timingSafeEqual(a, b);
}

async function findTokenIndex(tokens: { tokenHash: string; expiresAt: Date }[], rawToken: string): Promise<number> {
  const now = new Date();
  // Pass 1: Check sha256 entries first (fast O(n) synchronous comparison)
  for (let i = 0; i < tokens.length; i++) {
    const rt = tokens[i];
    if (rt.expiresAt < now) continue;
    if (rt.tokenHash.startsWith('sha256:')) {
      if (verifyTokenHashSync(rawToken, rt.tokenHash)) {
        return i;
      }
    }
  }

  // Pass 2: Fall back to legacy bcrypt entries only if no sha256 match found
  for (let i = 0; i < tokens.length; i++) {
    const rt = tokens[i];
    if (rt.expiresAt < now) continue;
    if (!rt.tokenHash.startsWith('sha256:')) {
      const match = await bcrypt.compare(rawToken, rt.tokenHash);
      if (match) {
        return i;
      }
    }
  }

  return -1;
}

/**
 * Refresh the access token using a valid refresh token.
 *
 * Rotates the refresh token: the old one is invalidated, a new one is
 * issued. This limits the window of a stolen refresh token.
 */
export async function refreshAccessToken(
  userId: string,
  rawRefreshToken: string,
  deviceName?: string,
): Promise<AuthTokens> {
  const user = await User.findById(userId);
  if (!user) {
    throw new UnauthorizedError('Invalid refresh token');
  }

  // Find a matching, non-expired refresh token (sha256 first, then legacy bcrypt)
  const matchedIndex = await findTokenIndex(user.refreshTokens, rawRefreshToken);

  if (matchedIndex === -1) {
    throw new UnauthorizedError('Invalid or expired refresh token');
  }

  // Remove the used refresh token (rotation)
  user.refreshTokens.splice(matchedIndex, 1);

  // Clean up expired tokens while we're at it
  purgeExpiredTokens(user);

  // Issue new token pair
  const tokens = await issueTokens(user, deviceName ?? null);

  logger.info({ userId: user._id }, 'Token refreshed');
  return tokens;
}

/**
 * Logout: revoke a specific refresh token.
 */
export async function logout(
  userId: string,
  rawRefreshToken: string,
): Promise<void> {
  const user = await User.findById(userId);
  if (!user) return; // Silently succeed — user already gone

  const index = await findTokenIndex(user.refreshTokens, rawRefreshToken);
  if (index !== -1) {
    user.refreshTokens.splice(index, 1);
    await user.save();
    logger.info({ userId: user._id }, 'User logged out (single session)');
    return;
  }

  // Token not found — still a successful logout (idempotent)
}

/**
 * Logout from all devices: revoke all refresh tokens.
 */
export async function logoutAll(userId: string): Promise<void> {
  const user = await User.findById(userId);
  if (!user) return;

  user.refreshTokens = [] as typeof user.refreshTokens;
  await user.save();

  logger.info({ userId: user._id }, 'User logged out (all sessions)');
}

/**
 * Get the current user's profile.
 */
export async function getCurrentUser(userId: string): Promise<UserProfile> {
  const user = await User.findById(userId);
  if (!user) {
    throw new NotFoundError('User');
  }
  return toUserProfile(user);
}

/**
 * Get all active sessions for the current user.
 */
export async function getSessions(
  userId: string,
  currentRefreshToken?: string,
): Promise<SessionInfo[]> {
  const user = await User.findById(userId);
  if (!user) {
    throw new NotFoundError('User');
  }

  purgeExpiredTokens(user);
  await user.save();

  const sessions: SessionInfo[] = [];
  for (const rt of user.refreshTokens) {
    let isCurrent = false;
    if (currentRefreshToken) {
      if (rt.tokenHash.startsWith('sha256:')) {
        isCurrent = verifyTokenHashSync(currentRefreshToken, rt.tokenHash);
      } else {
        isCurrent = await bcrypt.compare(currentRefreshToken, rt.tokenHash);
      }
    }
    sessions.push({
      id: rt._id.toString(),
      deviceName: rt.deviceName,
      createdAt: rt.createdAt,
      expiresAt: rt.expiresAt,
      isCurrent,
    });
  }

  return sessions;
}

/**
 * Revoke a specific session by its ID.
 */
export async function revokeSession(
  userId: string,
  sessionId: string,
): Promise<void> {
  const user = await User.findById(userId);
  if (!user) {
    throw new NotFoundError('User');
  }

  const index = user.refreshTokens.findIndex(
    (rt) => rt._id.toString() === sessionId,
  );
  if (index === -1) {
    throw new NotFoundError('Session');
  }

  user.refreshTokens.splice(index, 1);
  await user.save();

  logger.info({ userId, sessionId }, 'Session revoked');
}

/**
 * Change the current user's password.
 *
 * Requires the current password for verification. After changing,
 * all refresh tokens are revoked (force re-login on all devices).
 */
export async function changePassword(
  userId: string,
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  const user = await User.findById(userId);
  if (!user) {
    throw new NotFoundError('User');
  }

  const match = await user.comparePassword(currentPassword);
  if (!match) {
    throw new UnauthorizedError('Current password is incorrect');
  }

  user.password = newPassword; // Pre-save hook will hash it
  user.refreshTokens = [] as typeof user.refreshTokens; // Revoke all sessions
  await user.save();

  logger.info({ userId }, 'Password changed — all sessions revoked');
}

/**
 * Delete the current user's account.
 *
 * Requires password confirmation for safety.
 */
export async function deleteAccount(
  userId: string,
  password: string,
): Promise<void> {
  const user = await User.findById(userId);
  if (!user) {
    throw new NotFoundError('User');
  }

  const match = await user.comparePassword(password);
  if (!match) {
    throw new UnauthorizedError('Password is incorrect');
  }

  await User.deleteOne({ _id: userId });

  logger.info({ userId }, 'Account deleted');
}

// ─── Internal helpers ────────────────────────────────────────────────

/**
 * Issue a new access + refresh token pair and store the refresh token
 * hash on the user document.
 */
async function issueTokens(
  user: IUser,
  deviceName: string | null,
): Promise<AuthTokens> {
  const accessToken = generateAccessToken({
    userId: user._id.toString(),
    username: user.username,
  });

  const rawRefreshToken = generateRefreshToken();
  // Task H7: Store new refresh tokens with fast sha256 hash
  const tokenHash = hashRefreshToken(rawRefreshToken);

  user.refreshTokens.push({
    tokenHash,
    deviceName,
    createdAt: new Date(),
    expiresAt: getRefreshTokenExpiry(),
  } as any); // Mongoose will create the sub-document

  // Clean up expired tokens
  purgeExpiredTokens(user);

  // Task H7: Cap stored refresh tokens per user at 10 (drop oldest by createdAt)
  if (user.refreshTokens.length > 10) {
    user.refreshTokens.sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
    user.refreshTokens = user.refreshTokens.slice(user.refreshTokens.length - 10) as typeof user.refreshTokens;
  }

  await user.save();

  return {
    accessToken,
    refreshToken: rawRefreshToken,
  };
}

/**
 * Remove expired refresh tokens from the user document.
 */
function purgeExpiredTokens(user: IUser): void {
  const now = new Date();
  const valid = user.refreshTokens.filter((rt) => rt.expiresAt > now);
  if (valid.length !== user.refreshTokens.length) {
    user.refreshTokens = valid as typeof user.refreshTokens;
  }
}

/**
 * Map an IUser document to a safe UserProfile (no password, no tokens).
 */
function toUserProfile(user: IUser): UserProfile {
  return {
    id: user._id.toString(),
    username: user.username,
    email: user.email,
    lastLogin: user.lastLogin,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  };
}
