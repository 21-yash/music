/**
 * Cricbuzz cricket provider.
 *
 * Implements the SportsProvider interface by scraping Cricbuzz's
 * mobile site (m.cricbuzz.com) and extracting data from Next.js
 * RSC (React Server Components) streaming chunks.
 *
 * This is the only module that knows about Cricbuzz page structures.
 * The rest of the application uses the normalized types.
 */

import type { SportsProvider } from '../SportsProvider';
import type { MatchSummary, MatchDetails, Scorecard, Squad } from '../types';
import {
  fetchCricbuzzPage,
  extractRSCChunks,
  extractTypeMatches,
  extractUpcomingTypeMatches,
  extractJSONByKey,
} from './cricbuzzScraper';
import {
  mapTypeMatchesToSummaries,
  mapMatchDetails,
  mapScorecard,
  mapSquads,
} from './cricbuzzMapper';
import { logger } from '../../../utils/logger';

// ─── Cricbuzz URLs ───────────────────────────────────────────────────

const CRICBUZZ_BASE = 'https://m.cricbuzz.com';

const PAGES = {
  live: `${CRICBUZZ_BASE}/cricket-match/live-scores`,
  upcoming: `${CRICBUZZ_BASE}/cricket-match/live-scores/upcoming-matches`,
  recent: `${CRICBUZZ_BASE}/cricket-match/live-scores/recent-matches`,
  matchDetails: (id: string, slug: string) =>
    `${CRICBUZZ_BASE}/live-cricket-scores/${id}/${slug}`,
  scorecard: (id: string, slug: string) =>
    `${CRICBUZZ_BASE}/live-cricket-scorecard/${id}/${slug}`,
  squads: (id: string, slug: string) =>
    `${CRICBUZZ_BASE}/cricket-match-squads/${id}/${slug}`,
} as const;

// ─── Provider ────────────────────────────────────────────────────────

export class CricbuzzProvider implements SportsProvider {
  readonly name = 'Cricbuzz';
  readonly id = 'cricbuzz';

  /**
   * Get matches filtered by state.
   *
   * Fetches the appropriate Cricbuzz page, extracts RSC chunks,
   * parses the typeMatches array, and maps to normalized summaries.
   */
  async getMatches(
    filter: 'live' | 'upcoming' | 'recent',
  ): Promise<MatchSummary[]> {
    const url = PAGES[filter];
    const html = await fetchCricbuzzPage(url);
    const allChunks = extractRSCChunks(html);

    if (!allChunks) {
      logger.warn({ filter }, 'No RSC chunks extracted from Cricbuzz page');
      return [];
    }

    // Upcoming page uses a different RSC nesting structure
    const typeMatches =
      filter === 'upcoming'
        ? extractUpcomingTypeMatches(allChunks)
        : extractTypeMatches(allChunks, filter === 'live' ? 'live' : 'recent');

    if (typeMatches.length === 0) {
      logger.warn(
        { filter },
        'No typeMatches found — Cricbuzz page structure may have changed',
      );
      return [];
    }

    return mapTypeMatchesToSummaries(typeMatches, filter);
  }

  /**
   * Get full match details.
   *
   * Fetches the match's live-cricket-scores page and extracts the
   * commentaryPageData object, which contains the header, miniscore,
   * current batsmen/bowlers, partnership, and recent commentary.
   */
  async getMatchDetails(
    matchId: string,
    slug: string,
  ): Promise<MatchDetails | null> {
    const url = PAGES.matchDetails(matchId, slug);
    const html = await fetchCricbuzzPage(url);
    const allChunks = extractRSCChunks(html);

    if (!allChunks) {
      logger.warn({ matchId }, 'No RSC chunks for match details');
      return null;
    }

    // Extract commentaryPageData object
    const commentaryData = extractJSONByKey(
      allChunks,
      '"commentaryPageData":',
    ) as Record<string, unknown> | null;

    if (!commentaryData?.matchHeader) {
      logger.warn({ matchId }, 'commentaryPageData.matchHeader not found');
      return null;
    }

    return mapMatchDetails(commentaryData, matchId, slug);
  }

  /**
   * Get the full scorecard for a match.
   *
   * Fetches the scorecard page and extracts scorecardApiData + matchHeader.
   */
  async getScorecard(
    matchId: string,
    slug: string,
  ): Promise<Scorecard | null> {
    const url = PAGES.scorecard(matchId, slug);
    const html = await fetchCricbuzzPage(url, 15_000);
    const allChunks = extractRSCChunks(html);

    if (!allChunks) {
      logger.warn({ matchId }, 'No RSC chunks for scorecard');
      return null;
    }

    const scorecardApiData = (extractJSONByKey(
      allChunks,
      '"scorecardApiData":',
    ) || {}) as Record<string, unknown>;

    const matchHeader = (extractJSONByKey(
      allChunks,
      '"matchHeader":',
    ) || {}) as Record<string, unknown>;

    if (!scorecardApiData.scoreCard) {
      logger.warn({ matchId }, 'scorecardApiData.scoreCard not found');
      return null;
    }

    return mapScorecard(scorecardApiData, matchHeader, matchId);
  }

  /**
   * Get squad/team composition for a match.
   *
   * Extracts team1/team2 data blocks from the squads page.
   */
  async getSquads(
    matchId: string,
    slug: string,
  ): Promise<Squad | null> {
    const url = PAGES.squads(matchId, slug);
    const html = await fetchCricbuzzPage(url, 15_000);
    const allChunks = extractRSCChunks(html);

    if (!allChunks) {
      logger.warn({ matchId }, 'No RSC chunks for squads');
      return null;
    }

    // Extract team data using the scraper's helper
    const extractTeamFromChunks = (
      str: string,
      marker: string,
      key: string,
    ): Record<string, unknown> | null => {
      const markerIdx = str.indexOf(marker);
      if (markerIdx === -1) return null;
      return extractJSONByKey(str.slice(markerIdx), key.slice(key.indexOf('"'))) as Record<string, unknown> | null;
    };

    const team1Data = extractTeamFromChunks(
      allChunks,
      '"team1":{"team":',
      '"team1":',
    );
    const team2Data = extractTeamFromChunks(
      allChunks,
      '"team2":{"team":',
      '"team2":',
    );

    if (!team1Data && !team2Data) {
      logger.warn({ matchId }, 'Squad data not found in payload');
      return null;
    }

    return mapSquads(
      team1Data || {},
      team2Data || {},
      matchId,
    );
  }
}
