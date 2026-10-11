import { Router } from 'express';
import { authenticate } from '../middleware/authenticate';
import { validate } from '../middleware/validate';
import * as libraryController from '../controllers/libraryController';
import {
  likeSongSchema,
  checkLikesQuerySchema,
  syncLikesSchema,
  createPlaylistSchema,
  updatePlaylistSchema,
  addPlaylistSongSchema,
  reorderPlaylistSongsSchema,
  followTeamSchema,
  checkTeamsQuerySchema,
} from './schemas/librarySchemas';

const router = Router();

// All library routes require an authenticated user
router.use(authenticate);

// ─── Liked Songs ─────────────────────────────────────────────────────────────

router.get('/likes', libraryController.getLikedSongs);
router.post('/likes', validate({ body: likeSongSchema }), libraryController.likeSong);
router.delete('/likes/:songId', libraryController.unlikeSong);
router.get('/likes/check', validate({ query: checkLikesQuerySchema }), libraryController.checkLikedSongs);
router.post('/likes/sync', validate({ body: syncLikesSchema }), libraryController.syncLikes);
router.get('/likes/sync', libraryController.syncLikes);

// ─── Playlists ───────────────────────────────────────────────────────────────

router.get('/playlists', libraryController.getPlaylists);
router.post('/playlists', validate({ body: createPlaylistSchema }), libraryController.createPlaylist);
router.get('/playlists/:id', libraryController.getPlaylistById);
router.put('/playlists/:id', validate({ body: updatePlaylistSchema }), libraryController.updatePlaylist);
router.delete('/playlists/:id', libraryController.deletePlaylist);
router.post('/playlists/:id/songs', validate({ body: addPlaylistSongSchema }), libraryController.addSongToPlaylist);
router.delete('/playlists/:id/songs/:songId', libraryController.removeSongFromPlaylist);
router.put('/playlists/:id/songs/reorder', validate({ body: reorderPlaylistSongsSchema }), libraryController.reorderPlaylistSongs);

// ─── Followed Teams ──────────────────────────────────────────────────────────

router.get('/teams', libraryController.getFollowedTeams);
router.post('/teams', validate({ body: followTeamSchema }), libraryController.followTeam);
router.delete('/teams/:teamId', libraryController.unfollowTeam);
router.get('/teams/check', validate({ query: checkTeamsQuerySchema }), libraryController.checkFollowedTeams);

export default router;
