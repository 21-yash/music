import { Request, Response, NextFunction } from 'express';
import * as sportsService from '../services/sportsService';
import { sendSuccess } from '../utils/response';
import { NotFoundError } from '../utils/errors';
import { logger } from '../utils/logger';

/**
 * Sports controller.
 *
 * Handles HTTP requests for sports/cricket endpoints.
 * Sets Cache-Control headers to prevent client-side caching of live data.
 */

/**
 * Get matches filtered by state (live, upcoming, recent).
 *
 * Returns matches with a `fetchedAt` timestamp so the client can
 * verify data freshness and show "last updated X seconds ago".
 */
export async function getMatches(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const filter = req.query.filter as 'live' | 'upcoming' | 'recent';

    const result = await sportsService.getMatches(filter);

    // Prevent aggressive client caching — especially for live data
    if (filter === 'live') {
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    } else {
      res.setHeader('Cache-Control', 'public, max-age=60');
    }

    sendSuccess(res, result);
  } catch (error) {
    logger.error({ error }, 'Failed to get matches');
    next(error);
  }
}

/**
 * Get full match details (live score, batsmen, bowlers, commentary).
 */
export async function getMatchDetails(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const matchId = req.params.matchId as string;

    const result = await sportsService.getMatchDetails(matchId);
    if (!result) {
      throw new NotFoundError('Match');
    }

    // Live matches: no caching
    if (result.match.isLive) {
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    }

    sendSuccess(res, result);
  } catch (error) {
    logger.error({ error, matchId: req.params.matchId }, 'Failed to get match details');
    next(error);
  }
}

/**
 * Get the full scorecard for a match.
 */
export async function getScorecard(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const matchId = req.params.matchId as string;

    const result = await sportsService.getScorecard(matchId);
    if (!result) {
      throw new NotFoundError('Scorecard');
    }

    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    sendSuccess(res, result);
  } catch (error) {
    logger.error({ error, matchId: req.params.matchId }, 'Failed to get scorecard');
    next(error);
  }
}

/**
 * Get squad composition for a match.
 */
export async function getSquads(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const matchId = req.params.matchId as string;

    const result = await sportsService.getSquads(matchId);
    if (!result) {
      throw new NotFoundError('Squads');
    }

    res.setHeader('Cache-Control', 'public, max-age=300');
    sendSuccess(res, result);
  } catch (error) {
    logger.error({ error, matchId: req.params.matchId }, 'Failed to get squads');
    next(error);
  }
}
