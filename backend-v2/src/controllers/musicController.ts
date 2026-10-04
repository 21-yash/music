import { Request, Response, NextFunction } from 'express';
import * as musicService from '../services/musicService';
import { sendSuccess } from '../utils/response';
import { NotFoundError } from '../utils/errors';
import { logger } from '../utils/logger';

/**
 * Music controller.
 *
 * Handles HTTP requests, extracting parameters and delegating to the
 * musicService. Formats the response using sendSuccess/sendError.
 */

export async function search(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const query = req.query.q as string;
    const page = Number(req.query.page) || 1;
    const limit = Number(req.query.limit) || 20;
    const provider = req.query.provider as string | undefined;

    const results = await musicService.search(query, page, limit, provider);
    sendSuccess(res, results);
  } catch (error) {
    next(error);
  }
}

export async function getSong(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = req.params.id as string;
    const provider = req.query.provider as string | undefined;
    const song = await musicService.getSong(id, provider);

    if (!song) {
      throw new NotFoundError('Song');
    }

    sendSuccess(res, song);
  } catch (error) {
    next(error);
  }
}

export async function getAlbum(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = req.params.id as string;
    const provider = req.query.provider as string | undefined;
    const album = await musicService.getAlbum(id, provider);

    if (!album) {
      throw new NotFoundError('Album');
    }

    sendSuccess(res, album);
  } catch (error) {
    next(error);
  }
}

export async function getArtist(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = req.params.id as string;
    const provider = req.query.provider as string | undefined;
    const artist = await musicService.getArtist(id, provider);

    if (!artist) {
      throw new NotFoundError('Artist');
    }

    sendSuccess(res, artist);
  } catch (error) {
    next(error);
  }
}

export async function getPlaylist(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = req.params.id as string;
    const provider = req.query.provider as string | undefined;
    const playlist = await musicService.getPlaylist(id, provider);

    if (!playlist) {
      throw new NotFoundError('Playlist');
    }

    sendSuccess(res, playlist);
  } catch (error) {
    next(error);
  }
}

export async function getTrending(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const provider = req.query.provider as string | undefined;
    const songs = await musicService.getTrending(provider);
    sendSuccess(res, { songs });
  } catch (error) {
    next(error);
  }
}

/**
 * Resolve a stream URL for a song.
 *
 * Takes a song ID, looks up the encrypted media reference server-side,
 * decrypts it, and returns a direct CDN URL. The client never sees
 * the encrypted URL — it only knows the song ID.
 */
export async function stream(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const songId = (req.query.ref || req.query.id) as string;
    const quality = (req.query.quality as 'high' | 'medium' | 'low') || 'high';
    const provider = req.query.provider as string | undefined;

    if (!songId) {
      res.status(400).json({ success: false, error: { code: 'BAD_REQUEST', message: 'Song ID (ref) is required' } });
      return;
    }

    // Resolve the actual direct CDN URL from the provider (via Redis stream token lookup)
    const streamInfo = await musicService.resolveStreamUrl(songId, quality, provider);

    // Return the discriminated union directly
    sendSuccess(res, streamInfo);

  } catch (error) {
    logger.error({ error }, 'Stream error');
    next(error);
  }
}
