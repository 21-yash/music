import type { MusicProvider, Song, Album, Artist, Playlist, SearchResults, StreamInfo } from '../providers/music';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';
import { createHash } from 'node:crypto';

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
  streamToken: 'music:stoken:', // maps songId → encrypted streamRef
  streamResolved: 'music:sres:', // maps songId:quality → resolved StreamInfo
} as const;

// ─── Service ─────────────────────────────────────────────────────────

const _providers = new Map<string, MusicProvider>();
let _defaultProviderId = 'jiosaavn';

/**
 * Register a music provider with the service.
 * Call this during app startup for each provider.
 */
export function registerMusicProvider(provider: MusicProvider, isDefault: boolean = false): void {
  _providers.set(provider.name, provider);
  if (isDefault) {
    _defaultProviderId = provider.name;
  }
  logger.info({ provider: provider.name }, 'Music provider registered');
}

function getProvider(providerId?: string): MusicProvider {
  const id = providerId || _defaultProviderId;
  const provider = _providers.get(id);
  if (!provider) {
    throw new Error(`Music provider '${id}' not found or not initialized.`);
  }
  return provider;
}

/**
 * Search for songs, albums, and artists.
 */
export async function search(
  query: string,
  page: number,
  limit: number,
  providerId?: string
): Promise<SearchResults> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.search}${hashKey(query)}:${page}:${limit}:${provider.name}`;
  const cached = await getFromCache<SearchResults>(cacheKey);
  if (cached) return cached;

  const results = await provider.search(query, page, limit);

  // Store streamRefs in Redis and strip them from the response
  await storeStreamTokens(results.songs);
  const sanitized = { ...results, songs: results.songs.map(stripStreamRef) };

  await setCache(cacheKey, sanitized, CACHE_TTL.search);
  return sanitized;
}

/**
 * Get song details by ID.
 */
export async function getSong(id: string, providerId?: string): Promise<Song | null> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.song}${id}:${provider.name}`;
  const cached = await getFromCache<Song>(cacheKey);
  if (cached) return cached;

  const song = await provider.getSong(id);
  if (song) {
    await storeStreamTokens([song]);
    const sanitized = stripStreamRef(song);
    await setCache(cacheKey, sanitized, CACHE_TTL.song);
    return sanitized;
  }
  return song;
}

/**
 * Get album with songs.
 */
export async function getAlbum(id: string, providerId?: string): Promise<Album | null> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.album}${id}:${provider.name}`;
  const cached = await getFromCache<Album>(cacheKey);
  if (cached) return cached;

  const album = await provider.getAlbum(id);
  if (album) {
    await storeStreamTokens(album.songs);
    const sanitized = { ...album, songs: album.songs.map(stripStreamRef) };
    await setCache(cacheKey, sanitized, CACHE_TTL.album);
    return sanitized;
  }
  return album;
}

/**
 * Get artist with top songs and albums.
 */
export async function getArtist(id: string, providerId?: string): Promise<Artist | null> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.artist}${id}:${provider.name}`;
  const cached = await getFromCache<Artist>(cacheKey);
  if (cached) return cached;

  const artist = await provider.getArtist(id);
  if (artist) {
    await storeStreamTokens(artist.topSongs);
    const sanitized = { ...artist, topSongs: artist.topSongs.map(stripStreamRef) };
    await setCache(cacheKey, sanitized, CACHE_TTL.artist);
    return sanitized;
  }
  return artist;
}

/**
 * Get playlist with songs.
 */
export async function getPlaylist(id: string, providerId?: string): Promise<Playlist | null> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.playlist}${id}:${provider.name}`;
  const cached = await getFromCache<Playlist>(cacheKey);
  if (cached) return cached;

  const playlist = await provider.getPlaylist(id);
  if (playlist) {
    await storeStreamTokens(playlist.songs);
    const sanitized = { ...playlist, songs: playlist.songs.map(stripStreamRef) };
    await setCache(cacheKey, sanitized, CACHE_TTL.playlist);
    return sanitized;
  }
  return playlist;
}

/**
 * Get trending songs.
 */
export async function getTrending(providerId?: string): Promise<Song[]> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.trending}:${provider.name}`;
  const cached = await getFromCache<Song[]>(cacheKey);
  if (cached) return cached;

  const songs = await provider.getTrending();
  await storeStreamTokens(songs);
  const sanitized = songs.map(stripStreamRef);
  await setCache(cacheKey, sanitized, CACHE_TTL.trending);
  return sanitized;
}

/**
 * Resolve a stream URL from a song ID.
 *
 * Looks up the stored streamRef for the given song ID from Redis,
 * then delegates to the provider to decrypt/resolve the actual CDN URL.
 * This ensures the encrypted media URL never leaves the server.
 *
 * Not cached — stream URLs may be time-limited.
 */
export async function resolveStreamUrl(
  songId: string,
  quality: 'high' | 'medium' | 'low' = 'high',
  providerId?: string
): Promise<StreamInfo> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.streamResolved}${songId}:${quality}:${provider.name}`;
  const cached = await getFromCache<StreamInfo>(cacheKey);
  if (cached) return cached;

  // Look up the stored streamRef for this song ID
  let streamRef = await getStreamToken(songId);

  if (!streamRef) {
    // If not in Redis (expired/evicted), fetch fresh from provider
    const song = await provider.getSong(songId);
    if (!song || !song.streamRef) {
      throw new Error(`No playable stream found for song ${songId}`);
    }
    await storeStreamTokens([song]);
    streamRef = song.streamRef;
  }

  const result = await provider.resolveStreamUrl(streamRef, quality);
  // Cache the resolved result for 2 hours (CDN links are usually valid for 4-6 hours)
  await setCache(cacheKey, result, 2 * 60 * 60);
  
  return result;
}

// ─── Redis cache helpers ─────────────────────────────────────────────

/**
 * Hash a string for use in cache keys.
 * Prevents cache key injection and keeps keys a consistent length.
 */
function hashKey(input: string): string {
  return createHash('sha256').update(input).digest('hex').slice(0, 16);
}

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

// ─── Stream token helpers ────────────────────────────────────────────

/** TTL for stream tokens — 2 hours (generous, covers long listening sessions). */
const STREAM_TOKEN_TTL = 2 * 60 * 60;

/**
 * Strip the streamRef from a song before sending to the client.
 * The client uses the song ID to request streams, not the raw encrypted URL.
 */
function stripStreamRef(song: Song): Song {
  return { ...song, streamRef: '' };
}

/**
 * Store songId → streamRef mappings in Redis.
 * These are used by resolveStreamUrl to look up the encrypted URL server-side.
 */
async function storeStreamTokens(songs: Song[]): Promise<void> {
  try {
    const redis = getRedisClient();
    const pipeline = redis.pipeline();
    for (const song of songs) {
      if (song.streamRef) {
        pipeline.setex(`${CACHE_PREFIX.streamToken}${song.id}`, STREAM_TOKEN_TTL, song.streamRef);
      }
    }
    await pipeline.exec();
  } catch {
    // Non-fatal — resolveStreamUrl has a fallback to fetch fresh from provider
  }
}

/**
 * Retrieve the stored streamRef for a song ID.
 */
async function getStreamToken(songId: string): Promise<string | null> {
  try {
    const redis = getRedisClient();
    return await redis.get(`${CACHE_PREFIX.streamToken}${songId}`);
  } catch {
    return null;
  }
}
