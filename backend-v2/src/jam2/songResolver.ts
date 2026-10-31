import { Song } from './types';
import { getSong, getProviderInstance } from '../services/musicService';
import * as musicService from '../services/musicService';
import { logger } from '../utils/logger';

export async function storeStreamTokensFor(song: Song): Promise<void> {
  return musicService.storeStreamTokensFor(song);
}

export const DEFAULT_ALLOWED_HOST_PATTERNS = [
  /(^|\.)saavncdn\.com$/,
  /(^|\.)ytimg\.com$/,
  /(^|\.)googleusercontent\.com$/,
  /(^|\.)ggpht\.com$/,
  /(^|\.)scdn\.co$/,
];

export function getAllowedHostPatterns(): RegExp[] {
  const patterns = [...DEFAULT_ALLOWED_HOST_PATTERNS];
  const extra = process.env.JAM_ALLOWED_IMAGE_HOSTS;
  if (extra) {
    const hosts = extra.split(',').map((h) => h.trim()).filter(Boolean);
    for (const host of hosts) {
      const escaped = host.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      patterns.push(new RegExp(`(^|\\.)${escaped}$`));
    }
  }
  return patterns;
}

export function sanitizeUrl(url?: string | null): string | null {
  if (!url || typeof url !== 'string') return null;
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:') return null;
    const hostname = parsed.hostname.toLowerCase();
    const isAllowed = getAllowedHostPatterns().some((pattern) => pattern.test(hostname));
    if (!isAllowed) {
      logger.debug({ hostname, url }, 'sanitizeUrl rejected URL host');
      return null;
    }
    return parsed.href;
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

export function clearSongResolverCache(): void {
  memoryCache.clear();
}

/**
 * Merges client-provided metadata with resolved server metadata.
 * Task F3:
 * - title/artists/album/imageUrl: resolved value when non-empty else client value (sanitized + length-limited)
 * - duration: resolved.duration if > 0; else client duration only if 1..21600 else 0
 * - streamRef: resolved.streamRef or '' (never trust client)
 * - year/language/label/hasLyrics/playCount: resolved when present else client
 */
export function mergeResolvedSong(clientSong: Song, resolved: Song): Song {
  const title = (resolved.title && resolved.title.trim().length > 0)
    ? resolved.title.trim().slice(0, 200)
    : (clientSong.title || '').trim().slice(0, 200);

  const rawArtists = (resolved.artists && resolved.artists.length > 0)
    ? resolved.artists
    : (clientSong.artists || []);

  const artists = rawArtists
    .map((a) => ({
      id: (a.id || '').slice(0, 100),
      name: (a.name || '').trim().slice(0, 100),
      imageUrl: sanitizeUrl(a.imageUrl),
    }))
    .filter((a) => a.name.length > 0);

  const rawAlbum = resolved.album || clientSong.album;
  const album = rawAlbum && rawAlbum.title
    ? {
        id: (rawAlbum.id || '').slice(0, 100),
        title: (rawAlbum.title || '').trim().slice(0, 200),
        imageUrl: sanitizeUrl(rawAlbum.imageUrl),
      }
    : null;

  let duration = 0;
  if (resolved.duration && resolved.duration > 0) {
    duration = resolved.duration;
  } else if (typeof clientSong.duration === 'number' && clientSong.duration >= 1 && clientSong.duration <= 21600) {
    duration = clientSong.duration;
  } else {
    duration = 0;
  }

  const imageUrl = sanitizeUrl(resolved.imageUrl) || sanitizeUrl(clientSong.imageUrl);

  return {
    id: resolved.id || clientSong.id,
    title,
    artists,
    album,
    duration,
    imageUrl,
    year: (resolved.year || clientSong.year || '').slice(0, 20),
    language: (resolved.language || clientSong.language || '').slice(0, 50),
    hasLyrics: resolved.hasLyrics ?? clientSong.hasLyrics ?? false,
    playCount: resolved.playCount ?? clientSong.playCount ?? 0,
    label: (resolved.label || clientSong.label || '').slice(0, 200),
    streamRef: resolved.streamRef || '', // never trust client
    providerId: resolved.providerId || clientSong.providerId || '',
  };
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
      await musicService.storeStreamTokensFor(res);
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
      await musicService.storeStreamTokensFor(fetched);
      return normalized;
    }
  } catch (err) {
    logger.debug({ err, id, providerId }, 'Could not resolve song from musicService');
  }

  return null;
}

/**
 * Task F14: Resolves song metadata from provider, or falls back to strictly validated
 * client metadata if provider is unreliable (e.g. YouTube).
 * For reliable providers (e.g. JioSaavn), resolve failure returns null (rejected with INVALID).
 */
export async function resolveSongOrFallback(clientSong: Song): Promise<Song | null> {
  const providerId = clientSong.providerId || 'youtube';
  const resolved = await resolveSong(clientSong.id, providerId);
  if (resolved) {
    return mergeResolvedSong(clientSong, resolved);
  }

  const provider = getProviderInstance(providerId);
  const isReliable = provider ? (provider.getSongIsReliable !== false) : true;

  if (isReliable) {
    // For reliable providers, rejection on resolution failure is mandatory
    return null;
  }

  // Unreliable provider (e.g. YouTube) fallback: strictly validate client metadata
  const ytIdRegex = /^[A-Za-z0-9_-]{11}$/;
  if (providerId === 'youtube' && !ytIdRegex.test(clientSong.id)) {
    logger.debug({ id: clientSong.id, providerId }, 'Invalid YouTube ID shape in client metadata fallback');
    return null;
  }

  if (!clientSong.id || typeof clientSong.id !== 'string') {
    return null;
  }

  const title = (clientSong.title || '').trim().slice(0, 200);
  if (!title) {
    return null;
  }

  if (!Array.isArray(clientSong.artists) || clientSong.artists.length === 0) {
    return null;
  }

  const artists = clientSong.artists
    .map((a) => {
      const name = typeof a === 'string' ? (a as string).trim() : (a?.name || '').trim();
      const id = typeof a === 'object' && a?.id ? a.id : '';
      const img = typeof a === 'object' ? sanitizeUrl(a?.imageUrl) : null;
      return { id: (id || '').slice(0, 100), name: name.slice(0, 100), imageUrl: img };
    })
    .filter((a) => a.name.length > 0);

  if (artists.length === 0) {
    return null;
  }

  let duration = Number(clientSong.duration);
  if (isNaN(duration) || duration < 0 || duration > 21600) {
    duration = 0;
  }

  const imageUrl = sanitizeUrl(clientSong.imageUrl);

  const album = clientSong.album && clientSong.album.title
    ? {
        id: (clientSong.album.id || '').slice(0, 100),
        title: (clientSong.album.title || '').trim().slice(0, 200),
        imageUrl: sanitizeUrl(clientSong.album.imageUrl),
      }
    : null;

  logger.warn(
    { provider: providerId, id: clientSong.id },
    'jam2:songResolver:accepted_client_metadata_fallback',
  );

  await musicService.storeStreamTokensFor({
    ...clientSong,
    streamRef: clientSong.id,
    providerId,
  });

  return {
    id: clientSong.id,
    title,
    artists,
    album,
    duration,
    imageUrl,
    year: (clientSong.year || '').slice(0, 20),
    language: (clientSong.language || '').slice(0, 50),
    hasLyrics: Boolean(clientSong.hasLyrics),
    playCount: typeof clientSong.playCount === 'number' ? clientSong.playCount : 0,
    label: (clientSong.label || '').slice(0, 200),
    streamRef: '', // streamRef MUST be discarded; resolved on play
    providerId,
  };
}
