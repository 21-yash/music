import type { MusicProvider, Song, Album, Artist, Playlist, SearchResults, StreamInfo } from '../providers/music';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';

/**
 * Music service — business logic layer.
 *
 * Sits between the controller and the music provider, adding:
 * - Redis caching with appropriate TTLs
 * - Error logging
 * - Provider abstraction (the controller never touches the provider directly)
 *
 * The service holds a reference to a MusicProvider and delegates all
 * data fetching to it. Swapping providers is a single-line change.
 */

// ─── Cache TTLs (seconds) ────────────────────────────────────────────

const CACHE_TTL = {
  search: 5 * 60,       // 5 minutes — search results change often
  song: 30 * 60,        // 30 minutes — song metadata is stable
  album: 15 * 60,       // 15 minutes
  artist: 15 * 60,      // 15 minutes
  playlist: 15 * 60,    // 15 minutes
  trending: 10 * 60,    // 10 minutes
} as const;

// ─── Cache key prefixes ─────────────────────────────────────────────

const CACHE_PREFIX = {
  search: 'music:search:',
  song: 'music:song:',
  album: 'music:album:',
  artist: 'music:artist:',
  playlist: 'music:playlist:',
  trending: 'music:trending',
} as const;

// ─── Service ─────────────────────────────────────────────────────────

let _provider: MusicProvider | null = null;

/**
 * Initialize the music service with a provider.
 * Call this once during app startup.
 */
export function initMusicService(provider: MusicProvider): void {
  _provider = provider;
  logger.info({ provider: provider.name }, 'Music service initialized');
}

function getProvider(): MusicProvider {
  if (!_provider) {
    throw new Error('Music service not initialized. Call initMusicService() first.');
  }
  return _provider;
}

/**
 * Search for songs, albums, and artists.
 */
export async function search(
  query: string,
  page: number,
  limit: number,
): Promise<SearchResults> {
  const cacheKey = `${CACHE_PREFIX.search}${query}:${page}:${limit}`;
  const cached = await getFromCache<SearchResults>(cacheKey);
  if (cached) return cached;

  const results = await getProvider().search(query, page, limit);

  await setCache(cacheKey, results, CACHE_TTL.search);
  return results;
}

/**
 * Get song details by ID.
 */
export async function getSong(id: string): Promise<Song | null> {
  const cacheKey = `${CACHE_PREFIX.song}${id}`;
  const cached = await getFromCache<Song>(cacheKey);
  if (cached) return cached;

  const song = await getProvider().getSong(id);
  if (song) {
    await setCache(cacheKey, song, CACHE_TTL.song);
  }
  return song;
}

/**
 * Get album with songs.
 */
export async function getAlbum(id: string): Promise<Album | null> {
  const cacheKey = `${CACHE_PREFIX.album}${id}`;
  const cached = await getFromCache<Album>(cacheKey);
  if (cached) return cached;

  const album = await getProvider().getAlbum(id);
  if (album) {
    await setCache(cacheKey, album, CACHE_TTL.album);
  }
  return album;
}

/**
 * Get artist with top songs and albums.
 */
export async function getArtist(id: string): Promise<Artist | null> {
  const cacheKey = `${CACHE_PREFIX.artist}${id}`;
  const cached = await getFromCache<Artist>(cacheKey);
  if (cached) return cached;

  const artist = await getProvider().getArtist(id);
  if (artist) {
    await setCache(cacheKey, artist, CACHE_TTL.artist);
  }
  return artist;
}

/**
 * Get playlist with songs.
 */
export async function getPlaylist(id: string): Promise<Playlist | null> {
  const cacheKey = `${CACHE_PREFIX.playlist}${id}`;
  const cached = await getFromCache<Playlist>(cacheKey);
  if (cached) return cached;

  const playlist = await getProvider().getPlaylist(id);
  if (playlist) {
    await setCache(cacheKey, playlist, CACHE_TTL.playlist);
  }
  return playlist;
}

/**
 * Get trending songs.
 */
export async function getTrending(): Promise<Song[]> {
  const cacheKey = CACHE_PREFIX.trending;
  const cached = await getFromCache<Song[]>(cacheKey);
  if (cached) return cached;

  const songs = await getProvider().getTrending();
  await setCache(cacheKey, songs, CACHE_TTL.trending);
  return songs;
}

/**
 * Resolve a stream URL from an opaque stream reference.
 *
 * Not cached — stream URLs may be time-limited.
 */
export async function resolveStreamUrl(
  streamRef: string,
  quality: 'high' | 'medium' | 'low' = 'high',
): Promise<StreamInfo> {
  return getProvider().resolveStreamUrl(streamRef, quality);
}

// ─── Redis cache helpers ─────────────────────────────────────────────

async function getFromCache<T>(key: string): Promise<T | null> {
  try {
    const redis = getRedisClient();
    const data = await redis.get(key);
    if (!data) return null;
    return JSON.parse(data) as T;
  } catch {
    // Cache failures are non-fatal — fall through to provider
    return null;
  }
}

async function setCache(key: string, data: unknown, ttl: number): Promise<void> {
  try {
    const redis = getRedisClient();
    await redis.setex(key, ttl, JSON.stringify(data));
  } catch {
    // Cache write failures are non-fatal
  }
}
