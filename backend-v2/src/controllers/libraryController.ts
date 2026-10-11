import { Request, Response, NextFunction } from 'express';
import * as libraryService from '../services/libraryService';
import { sendSuccess } from '../utils/response';
import { BadRequestError } from '../utils/errors';

// ─── Liked Songs ─────────────────────────────────────────────────────────────

export async function getLikedSongs(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const page = parseInt(req.query.page as string, 10) || 1;
    const limit = parseInt(req.query.limit as string, 10) || 50;

    const result = await libraryService.getLikedSongs(userId, page, limit);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function likeSong(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const result = await libraryService.likeSong(userId, req.body);
    sendSuccess(res, result, 201, 'Song liked successfully');
  } catch (error) {
    next(error);
  }
}

export async function unlikeSong(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const songId = req.params.songId as string;
    const providerId = req.query.providerId as string | undefined;

    const result = await libraryService.unlikeSong(userId, songId, providerId);
    sendSuccess(res, result, 200, 'Song unliked successfully');
  } catch (error) {
    next(error);
  }
}

export async function checkLikedSongs(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const songIdsParam = req.query.songIds as string;
    if (!songIdsParam) {
      throw new BadRequestError('songIds parameter is required');
    }

    const songIds = songIdsParam.split(',').map((id) => id.trim()).filter(Boolean);
    const result = await libraryService.checkLikedSongs(userId, songIds);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function syncLikes(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const { pendingLikes, pendingUnlikes, since } = req.body || {};

    const result = await libraryService.syncLikes(
      userId,
      pendingLikes || [],
      pendingUnlikes || [],
      since,
    );
    sendSuccess(res, result, 200, 'Likes synced successfully');
  } catch (error) {
    next(error);
  }
}

// ─── Playlists ───────────────────────────────────────────────────────────────

export async function getPlaylists(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const result = await libraryService.getPlaylists(userId);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function createPlaylist(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const result = await libraryService.createPlaylist(userId, req.body);
    sendSuccess(res, result, 201, 'Playlist created successfully');
  } catch (error) {
    next(error);
  }
}

export async function getPlaylistById(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const result = await libraryService.getPlaylistById(userId, id);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function updatePlaylist(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const result = await libraryService.updatePlaylist(userId, id, req.body);
    sendSuccess(res, result, 200, 'Playlist updated successfully');
  } catch (error) {
    next(error);
  }
}

export async function deletePlaylist(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const result = await libraryService.deletePlaylist(userId, id);
    sendSuccess(res, result, 200, 'Playlist deleted successfully');
  } catch (error) {
    next(error);
  }
}

export async function addSongToPlaylist(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const result = await libraryService.addSongToPlaylist(userId, id, req.body);
    sendSuccess(res, result, 200, 'Song added to playlist');
  } catch (error) {
    next(error);
  }
}

export async function removeSongFromPlaylist(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const songId = req.params.songId as string;
    const result = await libraryService.removeSongFromPlaylist(userId, id, songId);
    sendSuccess(res, result, 200, 'Song removed from playlist');
  } catch (error) {
    next(error);
  }
}

export async function reorderPlaylistSongs(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const id = req.params.id as string;
    const { songIds } = req.body;
    const result = await libraryService.reorderPlaylistSongs(userId, id, songIds);
    sendSuccess(res, result, 200, 'Playlist reordered successfully');
  } catch (error) {
    next(error);
  }
}

// ─── Followed Teams ──────────────────────────────────────────────────────────

export async function getFollowedTeams(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const result = await libraryService.getFollowedTeams(userId);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}

export async function followTeam(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const result = await libraryService.followTeam(userId, req.body);
    sendSuccess(res, result, 201, 'Team followed successfully');
  } catch (error) {
    next(error);
  }
}

export async function unfollowTeam(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const teamId = req.params.teamId as string;
    const result = await libraryService.unfollowTeam(userId, teamId);
    sendSuccess(res, result, 200, 'Team unfollowed successfully');
  } catch (error) {
    next(error);
  }
}

export async function checkFollowedTeams(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const userId = req.user!.userId;
    const teamIdsParam = req.query.teamIds as string;
    if (!teamIdsParam) {
      throw new BadRequestError('teamIds parameter is required');
    }

    const teamIds = teamIdsParam.split(',').map((id) => id.trim()).filter(Boolean);
    const result = await libraryService.checkFollowedTeams(userId, teamIds);
    sendSuccess(res, result);
  } catch (error) {
    next(error);
  }
}
