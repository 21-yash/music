import { Song } from './types';
import { getSong } from '../services/musicService';
import { logger } from '../utils/logger';

export const ALLOWED_HOST_PATTERNS = [
  /(^|\.)saavncdn\.com$/,
  /(^|\.)ytimg\.com$/,
  /(^|\.)googleusercontent\.com$/,
  /(^|\.)ggpht\.com$/,
  /(^|\.)cricbuzz\.com$/,
  /(^|\.)scdn\.co$/,
  /(^|\.)spotifycdn\.com$/,
  /(^|\.)gravatar\.com$/,
  /(^|\.)githubusercontent\.com$/,
  /(^|\.)cloudinary\.com$/,
  /(^|\.)unsplash\.com$/,
  /(^|\.)example\.com$/,
];

export function sanitizeUrl(url?: string | null): string | null {
  if (!url || typeof url !== 'string') return null;
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:') return null;
    const hostname = parsed.hostname.toLowerCase();
    const isAllowed = ALLOWED_HOST_PATTERNS.some((pattern) => pattern.test(hostname));
    return isAllowed ? parsed.href : null;
  } catch {
    return null;
  }
}

interface CacheEntry {
  song: Song;
  expiresAt: number;
}

const memoryCache = new Map<string, CacheEntry>();
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

export type SongResolverFn = (id: string, providerId?: string) => Promise<Song | null>;

let customResolver: SongResolverFn | null = null;

export function setCustomSongResolver(resolver: SongResolverFn | null): void {
  customResolver = resolver;
}

export function cacheResolvedSong(song: Song): void {
  const key = `${song.providerId || 'default'}:${song.id}`;
  memoryCache.set(key, { song, expiresAt: Date.now() + CACHE_TTL_MS });
}

export async function resolveSong(id: string, providerId?: string): Promise<Song | null> {
  const key = `${providerId || 'default'}:${id}`;
  const cached = memoryCache.get(key);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.song;
  }

  if (customResolver) {
    const res = await customResolver(id, providerId);
    if (res) {
      cacheResolvedSong(res);
      return res;
    }
  }

  try {
    const fetched = await getSong(id, providerId);
    if (fetched) {
      const normalized: Song = {
        id: fetched.id,
        title: fetched.title,
        artists: (fetched.artists || []).map((a: { id: string; name: string; imageUrl?: string | null }) => ({
          id: a.id,
          name: a.name,
          imageUrl: sanitizeUrl(a.imageUrl),
        })),
        album: fetched.album
          ? {
              id: fetched.album.id,
              title: fetched.album.title,
              imageUrl: sanitizeUrl(fetched.album.imageUrl),
            }
          : null,
        duration: fetched.duration,
        imageUrl: sanitizeUrl(fetched.imageUrl),
        year: fetched.year || '',
        language: fetched.language || '',
        hasLyrics: fetched.hasLyrics || false,
        playCount: fetched.playCount || 0,
        label: fetched.label || '',
        streamRef: '',
        providerId: fetched.providerId || providerId || '',
      };
      cacheResolvedSong(normalized);
      return normalized;
    }
  } catch (err) {
    logger.debug({ err, id, providerId }, 'Could not resolve song from musicService');
  }

  return null;
}
