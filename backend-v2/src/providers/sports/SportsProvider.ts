import type {
  MatchSummary,
  MatchDetails,
  Scorecard,
  Squad,
  MatchState,
} from './types';

/**
 * Abstract sports provider interface.
 *
 * Every external sports data source (Cricbuzz, ESPNcricinfo, etc.)
 * must implement this interface. The rest of the application only
 * interacts with sports data through these normalized methods.
 *
 * Design decisions:
 * - Methods return normalized types from `./types.ts`.
 * - The `slug` parameter is an implementation detail needed by some providers
 *   (e.g., Cricbuzz requires it in URLs). The service layer stores slug mappings
 *   in Redis so the client only needs to send `matchId`.
 * - Match listing accepts a filter for live/upcoming/recent.
 */
export interface SportsProvider {
  /** Human-readable name for logging and debugging. */
  readonly name: string;

  /** Unique identifier. */
  readonly id: string;

  /**
   * Get a list of matches filtered by state.
   *
   * - 'live': Currently in progress
   * - 'upcoming': Scheduled, not yet started
   * - 'recent': Completed within the last few days
   */
  getMatches(filter: MatchState | 'live' | 'upcoming' | 'recent'): Promise<MatchSummary[]>;

  /**
   * Get full match details including live score, batsmen, bowlers,
   * partnership, and recent commentary.
   */
  getMatchDetails(matchId: string, slug: string): Promise<MatchDetails | null>;

  /**
   * Get the full scorecard for a match (all innings).
   */
  getScorecard(matchId: string, slug: string): Promise<Scorecard | null>;

  /**
   * Get squad/team composition for a match.
   */
  getSquads(matchId: string, slug: string): Promise<Squad | null>;
}
