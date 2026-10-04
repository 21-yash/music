/**
 * Sports service — business logic layer.
 *
 * Sits between the controller and the sports provider, adding:
 * - Redis caching with strict TTLs (no stale data fallback!)
 * - matchId → slug mapping in Redis (client only sends matchId)
 * - `fetchedAt` timestamps for client-side freshness verification
 * - Error logging
 *
 * ANTI-STALE-DATA DESIGN:
 * The old backend had a bug where stale data (1-2 hours old) would be
 * served because in-memory cache didn't strictly enforce TTLs. This
 * service uses Redis with strict EX TTLs, and NEVER falls back to
 * stale cached data if a fresh fetch fails. If the scrape fails,
 * the client gets an error — not hours-old scores.
 */

import type { SportsProvider } from '../providers/sports/SportsProvider';
import type { MatchSummary, MatchDetails, Scorecard, Squad } from '../providers/sports/types';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';

// ─── Cache configuration ─────────────────────────────────────────────

/**
 * Cache TTLs in seconds.
 *
 * These are intentionally short for live data to prevent stale scores.
 * Redis strictly enforces these — no "best effort" expiry.
 */
const CACHE_TTL = {
  matchesLive: 15,       // 15 seconds — score changes every ball
  matchesUpcoming: 300,  // 5 minutes — schedule rarely changes
  matchesRecent: 300,    // 5 minutes — results are final
  matchDetails: 15,      // 15 seconds — live commentary
  scorecard: 30,         // 30 seconds — per-over updates
  squads: 300,           // 5 minutes — static once announced
  slugMap: 86400,        // 24 hours — slugs don't change for a match
} as const;

const CACHE_PREFIX = {
  matches: 'sports:matches:',
  matchDetails: 'sports:match:',
  scorecard: 'sports:scorecard:',
  squads: 'sports:squads:',
  slugMap: 'sports:slug:',       // matchId → slug
} as const;

// ─── Service state ───────────────────────────────────────────────────

let _provider: SportsProvider | null = null;

/**
 * Initialize the sports service with a provider.
 * Call this once during app startup.
 */
export function initSportsService(provider: SportsProvider): void {
  _provider = provider;
  logger.info({ provider: provider.name }, 'Sports service initialized');
}

function getProvider(): SportsProvider {
  if (!_provider) {
    throw new Error('Sports service not initialized. Call initSportsService() first.');
  }
  return _provider;
}

// ─── Public API ──────────────────────────────────────────────────────

/**
 * Get matches filtered by state.
 *
 * Returns fresh data with a `fetchedAt` timestamp.
 * Also stores matchId → slug mappings for later use by detail endpoints.
 */
export async function getMatches(
  filter: 'live' | 'upcoming' | 'recent',
): Promise<{ matches: MatchSummary[]; fetchedAt: string }> {
  const cacheKey = `${CACHE_PREFIX.matches}${filter}`;
  const ttl = filter === 'live' ? CACHE_TTL.matchesLive : filter === 'upcoming' ? CACHE_TTL.matchesUpcoming : CACHE_TTL.matchesRecent;

  const cached = await getFromCache<{ matches: MatchSummary[]; fetchedAt: string }>(cacheKey);
  if (cached) return cached;

  const matches = await getProvider().getMatches(filter);

  // Store slug mappings for all matches (so client only needs to send matchId later)
  await storeSlugMappings(matches);

  const result = {
    matches,
    fetchedAt: new Date().toISOString(),
  };

  await setCache(cacheKey, result, ttl);
  return result;
}

/**
 * Get full match details.
 *
 * The client sends only `matchId`. The service looks up the slug from Redis.
 * If the slug is not found, an error is thrown — the client should first
 * call the list endpoint so slugs get populated.
 */
export async function getMatchDetails(
  matchId: string,
): Promise<{ match: MatchDetails; fetchedAt: string } | null> {
  const cacheKey = `${CACHE_PREFIX.matchDetails}${matchId}`;

  const cached = await getFromCache<{ match: MatchDetails; fetchedAt: string }>(cacheKey);
  if (cached) return cached;

  const slug = await getSlug(matchId);
  if (!slug) {
    throw new Error(`No slug mapping found for match ${matchId}. Ensure the match list was fetched first.`);
  }

  const match = await getProvider().getMatchDetails(matchId, slug);
  if (!match) return null;

  const result = {
    match,
    fetchedAt: new Date().toISOString(),
  };

  await setCache(cacheKey, result, CACHE_TTL.matchDetails);
  return result;
}

/**
 * Get scorecard for a match.
 */
export async function getScorecard(
  matchId: string,
): Promise<{ scorecard: Scorecard; fetchedAt: string } | null> {
  const cacheKey = `${CACHE_PREFIX.scorecard}${matchId}`;

  const cached = await getFromCache<{ scorecard: Scorecard; fetchedAt: string }>(cacheKey);
  if (cached) return cached;

  const slug = await getSlug(matchId);
  if (!slug) {
    throw new Error(`No slug mapping found for match ${matchId}. Ensure the match list was fetched first.`);
  }

  const scorecard = await getProvider().getScorecard(matchId, slug);
  if (!scorecard) return null;

  const result = {
    scorecard,
    fetchedAt: new Date().toISOString(),
  };

  await setCache(cacheKey, result, CACHE_TTL.scorecard);
  return result;
}

/**
 * Get squads for a match.
 */
export async function getSquads(
  matchId: string,
): Promise<{ squads: Squad; fetchedAt: string } | null> {
  const cacheKey = `${CACHE_PREFIX.squads}${matchId}`;

  const cached = await getFromCache<{ squads: Squad; fetchedAt: string }>(cacheKey);
  if (cached) return cached;

  const slug = await getSlug(matchId);
  if (!slug) {
    throw new Error(`No slug mapping found for match ${matchId}. Ensure the match list was fetched first.`);
  }

  const squads = await getProvider().getSquads(matchId, slug);
  if (!squads) return null;

  const result = {
    squads,
    fetchedAt: new Date().toISOString(),
  };

  await setCache(cacheKey, result, CACHE_TTL.squads);
  return result;
}

// ─── Slug mapping ────────────────────────────────────────────────────

/**
 * Store matchId → slug mappings in Redis.
 *
 * Called after every match list fetch. Uses pipeline for efficiency.
 */
async function storeSlugMappings(matches: MatchSummary[]): Promise<void> {
  try {
    const redis = getRedisClient();
    const pipeline = redis.pipeline();

    for (const match of matches) {
      if (match.id && match.slug) {
        pipeline.setex(
          `${CACHE_PREFIX.slugMap}${match.id}`,
          CACHE_TTL.slugMap,
          match.slug,
        );
      }
    }

    await pipeline.exec();
  } catch {
    // Non-fatal — detail endpoints will fail gracefully
  }
}

/**
 * Look up the slug for a match ID.
 */
async function getSlug(matchId: string): Promise<string | null> {
  try {
    const redis = getRedisClient();
    return await redis.get(`${CACHE_PREFIX.slugMap}${matchId}`);
  } catch {
    return null;
  }
}

// ─── Redis cache helpers ─────────────────────────────────────────────

async function getFromCache<T>(key: string): Promise<T | null> {
  try {
    const redis = getRedisClient();
    const data = await redis.get(key);
    if (!data) return null;
    return JSON.parse(data) as T;
  } catch {
    // Cache failures are non-fatal — fall through to provider
    // This is NOT stale-data fallback — this is a Redis connection error
    return null;
  }
}

async function setCache(key: string, data: unknown, ttl: number): Promise<void> {
  try {
    const redis = getRedisClient();
    await redis.setex(key, ttl, JSON.stringify(data));
  } catch {
    // Cache write failures are non-fatal
  }
}
