import { Router } from 'express';
import * as musicController from '../controllers/musicController';
import { validate } from '../middleware/validate';
import { authenticate } from '../middleware/authenticate';
import { requireApiKey } from '../middleware/apiKey';
import { musicLimiter, searchLimiter, streamLimiter } from '../middleware/rateLimit';
import { searchSchema, idParamSchema, streamSchema } from './schemas/musicSchemas';

const router = Router();

// All music routes require valid API key and authenticated session
router.use(requireApiKey, authenticate);

/** Search songs, albums, artists */
router.get('/search', searchLimiter, validate({ query: searchSchema.shape.query }), musicController.search);

/** Get trending songs */
router.get('/trending', musicLimiter, musicController.getTrending);

/** Get home data (featured releases, top playlists) */
router.get('/home', musicLimiter, musicController.getHomeData);

/** Proxy audio stream */
router.get('/stream', streamLimiter, validate({ query: streamSchema.shape.query }), musicController.stream);

/** Get song details */
router.get('/songs/:id', musicLimiter, validate({ params: idParamSchema }), musicController.getSong);

/** Get album details */
router.get('/albums/:id', musicLimiter, validate({ params: idParamSchema }), musicController.getAlbum);

/** Get artist details */
router.get('/artists/:id', musicLimiter, validate({ params: idParamSchema }), musicController.getArtist);

/** Get playlist details */
router.get('/playlists/:id', musicLimiter, validate({ params: idParamSchema }), musicController.getPlaylist);

/** Get related/recommended songs */
router.get('/related', musicLimiter, musicController.getRelated);

/** Get synchronized or plain lyrics */
router.get('/lyrics', musicLimiter, musicController.getLyrics);

export default router;

