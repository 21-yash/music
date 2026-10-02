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

    // Fetch the stream from the provider's CDN
    // We proxy it to avoid CORS issues and to hide the direct URL.
    const headers = new Headers();
    if (req.headers.range) {
      headers.set('Range', req.headers.range);
    }

    const response = await fetch(streamInfo.url, {
      headers,
      // Pass along any standard abort signal
      signal: req.signal
    });

    if (!response.ok) {
      logger.error({ status: response.status, url: streamInfo.url }, 'Failed to fetch stream from CDN');
      res.status(502).json({ success: false, error: { code: 'BAD_GATEWAY', message: 'Failed to retrieve audio stream' } });
      return;
    }

    // Proxy the headers
    response.headers.forEach((value, key) => {
      // Forward relevant headers, especially Content-Type, Content-Length, Content-Range, Accept-Ranges
      if (['content-type', 'content-length', 'content-range', 'accept-ranges', 'cache-control'].includes(key.toLowerCase())) {
        res.setHeader(key, value);
      }
    });

    res.status(response.status); // Forward 200 or 206

    // Pipe the response body to the express response
    if (response.body) {
      // Create a node-readable stream from the web stream and pipe it
      const reader = response.body.getReader();
      const pump = async () => {
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            res.write(value);
          }
          res.end();
        } catch (err) {
          logger.error({ err }, 'Stream piping error');
          res.end();
        }
      };
      pump();
    } else {
      res.end();
    }
  } catch (error) {
    // If it's a known error, we can handle it
    logger.error({ error }, 'Stream error');
    next(error);
  }
}
