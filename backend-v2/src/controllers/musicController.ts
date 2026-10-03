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

    const results = await musicService.search(query, page, limit);
    sendSuccess(res, results);
  } catch (error) {
    next(error);
  }
}

export async function getSong(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = req.params.id as string;
    const song = await musicService.getSong(id);

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
    const album = await musicService.getAlbum(id);

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
    const artist = await musicService.getArtist(id);

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
    const playlist = await musicService.getPlaylist(id);

    if (!playlist) {
      throw new NotFoundError('Playlist');
    }

    sendSuccess(res, playlist);
  } catch (error) {
    next(error);
  }
}

export async function getTrending(_req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const songs = await musicService.getTrending();
    sendSuccess(res, { songs });
  } catch (error) {
    next(error);
  }
}

/**
 * Proxy the audio stream.
 *
 * Resolves the opaque streamRef into a CDN URL, fetches the audio,
 * and pipes it to the client with support for Range requests.
 * This is crucial for ExoPlayer/Media3 playback.
 */
export async function stream(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const streamRef = req.query.ref as string;
    const quality = (req.query.quality as 'high' | 'medium' | 'low') || 'high';

    if (!streamRef) {
      res.status(400).json({ success: false, error: { code: 'BAD_REQUEST', message: 'Stream reference is required' } });
      return;
    }

    // Resolve the actual direct CDN URL from the provider
    const streamInfo = await musicService.resolveStreamUrl(streamRef, quality);

    // 🚀 OPTIMIZATION: Instead of proxying the massive audio stream through our Render server
    // (which causes slow load times, huge latency, and burns server bandwidth), 
    // we return the direct CDN URL to the client. 
    // Android ExoPlayer (and HTML5 Audio) will stream directly from YouTube/JioSaavn's Edge CDNs!
    sendSuccess(res, {
      url: streamInfo.url,
      quality: streamInfo.quality,
      contentType: streamInfo.contentType
    });

  } catch (error) {
    // If it's a known error, we can handle it
    logger.error({ error }, 'Stream error');
    next(error);
  }
}
