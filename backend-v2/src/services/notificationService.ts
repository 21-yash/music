import { Types } from 'mongoose';
import {
  NotificationSettings,
  FollowedTeam,
  ISportsNotificationPrefs,
} from '../models';

export const DEFAULT_PREFERENCES: ISportsNotificationPrefs = {
  enabled: true,
  matchStartingSoon: true,
  matchStarted: true,
  matchConcluded: true,
  milestones: true,
  matchUpdates: true,
};

function sanitizePrefs(
  prefs?: Partial<ISportsNotificationPrefs> | null,
): ISportsNotificationPrefs {
  return {
    enabled: prefs?.enabled ?? DEFAULT_PREFERENCES.enabled,
    matchStartingSoon: prefs?.matchStartingSoon ?? DEFAULT_PREFERENCES.matchStartingSoon,
    matchStarted: prefs?.matchStarted ?? DEFAULT_PREFERENCES.matchStarted,
    matchConcluded: prefs?.matchConcluded ?? DEFAULT_PREFERENCES.matchConcluded,
    milestones: prefs?.milestones ?? DEFAULT_PREFERENCES.milestones,
    matchUpdates: prefs?.matchUpdates ?? DEFAULT_PREFERENCES.matchUpdates,
  };
}

export async function getPreferences(userId: string): Promise<ISportsNotificationPrefs> {
  const userObjectId = new Types.ObjectId(userId);
  const doc = await NotificationSettings.findOne({ userId: userObjectId }).lean();
  return sanitizePrefs(doc?.preferences);
}

export async function updatePreferences(
  userId: string,
  updates: Partial<ISportsNotificationPrefs>,
): Promise<ISportsNotificationPrefs> {
  const userObjectId = new Types.ObjectId(userId);
  const setFields: Record<string, boolean> = {};
  for (const [k, v] of Object.entries(updates)) {
    if (typeof v === 'boolean') {
      setFields[`preferences.${k}`] = v;
    }
  }

  const updated = await NotificationSettings.findOneAndUpdate(
    { userId: userObjectId },
    { $set: setFields },
    { upsert: true, new: true, setDefaultsOnInsert: true },
  ).lean();

  return sanitizePrefs(updated?.preferences);
}

export async function registerFcmToken(
  userId: string,
  token: string,
  platform: 'android' = 'android',
): Promise<{ registered: boolean }> {
  const userObjectId = new Types.ObjectId(userId);
  const now = new Date();

  // Remove this token from any other user (device handover / re-login) and from current user before re-adding
  await NotificationSettings.updateMany(
    { 'fcmTokens.token': token },
    { $pull: { fcmTokens: { token } } },
  );

  await NotificationSettings.findOneAndUpdate(
    { userId: userObjectId },
    {
      $push: {
        fcmTokens: {
          $each: [{ token, platform, updatedAt: now }],
          $slice: -5,
        },
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true },
  );

  return { registered: true };
}

export async function unregisterFcmToken(
  userId: string,
  token: string,
): Promise<{ removed: boolean }> {
  const userObjectId = new Types.ObjectId(userId);
  const res = await NotificationSettings.updateOne(
    { userId: userObjectId },
    { $pull: { fcmTokens: { token } } },
  );
  return { removed: res.modifiedCount > 0 };
}

export async function removeFcmTokens(tokens: string[]): Promise<void> {
  if (tokens.length === 0) return;
  await NotificationSettings.updateMany(
    { 'fcmTokens.token': { $in: tokens } },
    { $pull: { fcmTokens: { token: { $in: tokens } } } },
  );
}

export interface ActiveSubscriber {
  userId: string;
  tokens: string[];
  preferences: ISportsNotificationPrefs;
  followedTeamIds: Set<string>;
  followedShortNames: Set<string>;
  followedTeamNames: Set<string>;
}

/**
 * Returns only users with >= 1 registered FCM token, notifications.enabled == true,
 * and >= 1 followed team so background polling can short-circuit cleanly when empty.
 */
export async function getActiveSportsSubscribers(): Promise<ActiveSubscriber[]> {
  const settingsDocs = await NotificationSettings.find({
    'preferences.enabled': { $ne: false },
    'fcmTokens.0': { $exists: true },
  })
    .select('userId fcmTokens.token preferences')
    .lean();

  if (settingsDocs.length === 0) return [];

  const userIds = settingsDocs.map((d) => d.userId);
  const followedDocs = await FollowedTeam.find({
    userId: { $in: userIds },
  })
    .select('userId teamId shortName teamName')
    .lean();

  if (followedDocs.length === 0) return [];

  const teamsByUser = new Map<
    string,
    { ids: Set<string>; shorts: Set<string>; names: Set<string> }
  >();
  for (const ft of followedDocs) {
    const key = String(ft.userId);
    let bucket = teamsByUser.get(key);
    if (!bucket) {
      bucket = { ids: new Set(), shorts: new Set(), names: new Set() };
      teamsByUser.set(key, bucket);
    }
    if (ft.teamId) bucket.ids.add(ft.teamId.trim().toLowerCase());
    if (ft.shortName) bucket.shorts.add(ft.shortName.trim().toLowerCase());
    if (ft.teamName) bucket.names.add(ft.teamName.trim().toLowerCase());
  }

  const subscribers: ActiveSubscriber[] = [];
  for (const doc of settingsDocs) {
    const uid = String(doc.userId);
    const teams = teamsByUser.get(uid);
    if (!teams) continue;
    const tokens = (doc.fcmTokens || []).map((t) => t.token).filter(Boolean);
    if (tokens.length === 0) continue;

    subscribers.push({
      userId: uid,
      tokens,
      preferences: sanitizePrefs(doc.preferences),
      followedTeamIds: teams.ids,
      followedShortNames: teams.shorts,
      followedTeamNames: teams.names,
    });
  }

  return subscribers;
}
