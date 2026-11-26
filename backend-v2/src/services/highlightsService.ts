import type { MatchHighlight, MatchHighlightsResult } from '../providers/sports/types';
import * as sportsService from './sportsService';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';
import {
  isBcciCandidateMatch,
  isIplMatch,
  type MatchCandidateContext,
} from '../providers/sports/highlights/highlightMatcher';
import {
  bcciHighlightsProvider,
  iplHighlightsProvider,
} from '../providers/sports/highlights/MuxBffHighlightsProvider';
import { youtubeHighlightsProvider } from '../providers/sports/highlights/YouTubeHighlightsProvider';

const CACHE_PREFIX = 'sports:highlights:match:';
const CACHE_TTL_FOUND_SECONDS = 600; // 10 minutes — safely below Mux JWT expiry
const CACHE_TTL_EMPTY_SECONDS = 300; // 5 minutes — re-check soon after match ends

export interface HighlightsProviderSet {
  bcci: { findHighlights(match: MatchCandidateContext): Promise<MatchHighlight[]> };
  ipl: { findHighlights(match: MatchCandidateContext): Promise<MatchHighlight[]> };
  youtube: { findHighlights(match: MatchCandidateContext): Promise<MatchHighlight[]> };
}

const defaultProviders: HighlightsProviderSet = {
  bcci: bcciHighlightsProvider,
  ipl: iplHighlightsProvider,
  youtube: youtubeHighlightsProvider,
};

/**
 * Fetch and cache normalized match highlights across IPLT20, BCCI.tv, and official YouTube channels.
 */
export async function getMatchHighlights(
  matchId: string,
  providers: HighlightsProviderSet = defaultProviders,
): Promise<MatchHighlightsResult | null> {
  const detailsResult = await sportsService.getMatchDetails(matchId);
  if (!detailsResult) {
    return null;
  }

  const match = detailsResult.match;

  // Upcoming matches cannot have highlights yet
  if (match.state === 'upcoming') {
    return {
      matchId,
      highlights: [],
      fetchedAt: new Date().toISOString(),
    };
  }

  const cacheKey = `${CACHE_PREFIX}${matchId}`;
  const cached = await getCachedHighlights(cacheKey);
  if (cached) {
    return cached;
  }

  const context: MatchCandidateContext = {
    team1Name: match.team1.name,
    team1Short: match.team1.shortName,
    team2Name: match.team2.name,
    team2Short: match.team2.shortName,
    series: match.series,
    matchDesc: match.matchDesc,
    format: match.format,
    startTimestamp: match.startTimestamp,
  };

  let highlights: MatchHighlight[] = [];

  if (isIplMatch(match.series)) {
    highlights = await providers.ipl.findHighlights(context);
    if (highlights.length === 0) {
      highlights = await providers.youtube.findHighlights(context);
    }
  } else if (isBcciCandidateMatch(context)) {
    highlights = await providers.bcci.findHighlights(context);
    // India away tours or ICC events aren't hosted on bcci.tv; fall back to YouTube
    if (highlights.length === 0) {
      highlights = await providers.youtube.findHighlights(context);
    }
  } else {
    highlights = await providers.youtube.findHighlights(context);
  }

  const result: MatchHighlightsResult = {
    matchId,
    highlights,
    fetchedAt: new Date().toISOString(),
  };

  const ttl = highlights.length > 0 ? CACHE_TTL_FOUND_SECONDS : CACHE_TTL_EMPTY_SECONDS;
  await setCachedHighlights(cacheKey, result, ttl);

  logger.info(
    { matchId, count: highlights.length, source: highlights[0]?.source ?? 'none' },
    'Resolved match highlights',
  );

  return result;
}

async function getCachedHighlights(key: string): Promise<MatchHighlightsResult | null> {
  try {
    const redis = getRedisClient();
    const raw = await redis.get(key);
    return raw ? (JSON.parse(raw) as MatchHighlightsResult) : null;
  } catch {
    return null;
  }
}

async function setCachedHighlights(
  key: string,
  value: MatchHighlightsResult,
  ttlSeconds: number,
): Promise<void> {
  try {
    const redis = getRedisClient();
    await redis.setex(key, ttlSeconds, JSON.stringify(value));
  } catch {
    // Non-fatal Redis error
  }
}
