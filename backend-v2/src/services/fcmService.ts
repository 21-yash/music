import { initializeApp, getApps, getApp, cert } from 'firebase-admin/app';
import { getMessaging as getFirebaseMessaging, type Messaging } from 'firebase-admin/messaging';
import { env } from '../config/env';
import { logger } from '../utils/logger';
import { removeFcmTokens } from './notificationService';

export interface SportsPushMessage {
  title: string;
  body: string;
  matchId: string;
  eventType:
    | 'match_starting_soon'
    | 'match_started'
    | 'match_concluded'
    | 'milestone'
    | 'match_update';
  channelId: 'match_lifecycle' | 'match_events';
}

const DEAD_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
  'messaging/mismatched-credential',
]);

let messagingInstance: Messaging | null = null;
let initAttempted = false;

function getMessaging(): Messaging | null {
  if (messagingInstance) return messagingInstance;
  if (initAttempted) return null;
  initAttempted = true;

  const projectId = env.FIREBASE_PROJECT_ID?.trim();
  const clientEmail = env.FIREBASE_CLIENT_EMAIL?.trim();
  const privateKeyRaw = env.FIREBASE_PRIVATE_KEY?.trim();

  if (!projectId || !clientEmail || !privateKeyRaw) {
    logger.info('Firebase Admin credentials not configured — FCM sends are disabled');
    return null;
  }

  try {
    const privateKey = privateKeyRaw.replace(/^["']|["']$/g, '').replace(/\\n/g, '\n');
    const app =
      getApps().length > 0
        ? getApp()
        : initializeApp({
            credential: cert({
              projectId,
              clientEmail,
              privateKey,
            }),
          });
    messagingInstance = getFirebaseMessaging(app);
    logger.info({ projectId }, 'Firebase Cloud Messaging initialized');
    return messagingInstance;
  } catch (error) {
    logger.error({ error }, 'Failed to initialize Firebase Admin SDK');
    return null;
  }
}

export async function sendPushToTokens(
  tokens: string[],
  payload: SportsPushMessage,
): Promise<{ sent: number; failed: number; transportFailed: boolean }> {
  const uniqueTokens = Array.from(new Set(tokens.filter((t) => t.trim().length > 0)));
  if (uniqueTokens.length === 0) return { sent: 0, failed: 0, transportFailed: false };

  const messaging = getMessaging();
  if (!messaging) {
    logger.debug(
      { matchId: payload.matchId, eventType: payload.eventType, targets: uniqueTokens.length },
      'Skipped FCM send (credentials not configured)',
    );
    return { sent: 0, failed: 0, transportFailed: false };
  }

  const BATCH_SIZE = 500;
  let sent = 0;
  let failed = 0;
  let transportErrorBatches = 0;
  let totalBatches = 0;
  const deadTokens: string[] = [];

  for (let i = 0; i < uniqueTokens.length; i += BATCH_SIZE) {
    totalBatches += 1;
    const batch = uniqueTokens.slice(i, i + BATCH_SIZE);
    try {
      const response = await messaging.sendEachForMulticast({
        tokens: batch,
        data: {
          matchId: payload.matchId,
          eventType: payload.eventType,
          channelId: payload.channelId,
          title: payload.title.slice(0, 180),
          body: payload.body.slice(0, 500),
          deepLink: `synq://sports/match/${payload.matchId}`,
        },
        android: {
          priority: 'high',
          ttl: 300_000,
        },
      });

      sent += response.successCount;
      failed += response.failureCount;

      response.responses.forEach((res, idx) => {
        if (!res.success && res.error && DEAD_TOKEN_CODES.has(res.error.code)) {
          deadTokens.push(batch[idx]);
        }
      });
    } catch (error) {
      transportErrorBatches += 1;
      failed += batch.length;
      logger.error({ error, matchId: payload.matchId }, 'FCM multicast send failed');
    }
  }

  if (deadTokens.length > 0) {
    await removeFcmTokens(deadTokens).catch((err) =>
      logger.warn({ err, count: deadTokens.length }, 'Failed to prune stale FCM tokens'),
    );
  }

  return {
    sent,
    failed,
    transportFailed: sent === 0 && transportErrorBatches === totalBatches && totalBatches > 0,
  };
}
