import { Router } from 'express';
import * as sportsController from '../controllers/sportsController';
import { validate } from '../middleware/validate';
import { authenticate } from '../middleware/authenticate';
import { requireApiKey } from '../middleware/apiKey';
import { sportsLimiter } from '../middleware/rateLimit';
import { matchesSchema, matchIdSchema } from './schemas/sportsSchemas';

/**
 * Sports routes.
 *
 * All endpoints are under /api/v1/sports/cricket/...
 *
 * Endpoints:
 *   GET /matches?filter=live|upcoming|recent  — Match listings
 *   GET /matches/:matchId                     — Full match details
 *   GET /matches/:matchId/scorecard           — Full scorecard
 *   GET /matches/:matchId/squads              — Playing XI & bench
 */
const router = Router();

// All sports routes require valid API key, authenticated session, and rate limiting
router.use(requireApiKey, authenticate);
router.use(sportsLimiter);


router.get(
  '/matches',
  validate(matchesSchema),
  sportsController.getMatches,
);

router.get(
  '/matches/:matchId',
  validate(matchIdSchema),
  sportsController.getMatchDetails,
);

router.get(
  '/matches/:matchId/scorecard',
  validate(matchIdSchema),
  sportsController.getScorecard,
);

router.get(
  '/matches/:matchId/squads',
  validate(matchIdSchema),
  sportsController.getSquads,
);

export default router;
