import { Router } from 'express';
import * as musicController from '../controllers/musicController';
import { validate } from '../middleware/validate';
import { searchSchema, idParamSchema, streamSchema } from './schemas/musicSchemas';

const router = Router();

// ─── Public routes (no auth required) ─────────────────────────────────

/** Search songs, albums, artists */
router.get('/search', validate({ query: searchSchema.shape.query }), musicController.search);

/** Get trending songs */
router.get('/trending', musicController.getTrending);

/** Get home data (featured releases, top playlists) */
router.get('/home', musicController.getHomeData);

/** Proxy audio stream */
router.get('/stream', validate({ query: streamSchema.shape.query }), musicController.stream);

/** Get song details */
router.get('/songs/:id', validate({ params: idParamSchema }), musicController.getSong);

/** Get album details */
router.get('/albums/:id', validate({ params: idParamSchema }), musicController.getAlbum);

/** Get artist details */
router.get('/artists/:id', validate({ params: idParamSchema }), musicController.getArtist);

/** Get playlist details */
router.get('/playlists/:id', validate({ params: idParamSchema }), musicController.getPlaylist);

export default router;
