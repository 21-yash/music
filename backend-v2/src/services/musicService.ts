import type { MusicProvider, Song, Album, Artist, Playlist, SearchResults, StreamInfo } from '../providers/music';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';
import { createHash } from 'node:crypto';
import { Types } from 'mongoose';
import { Playlist as PlaylistModel } from '../models/Playlist';
import { User } from '../models/User';
import { upgradeImageUrl } from '../providers/music/jiosaavn/jiosaavnMapper';
import { deduplicateRelatedSongs } from '../utils/musicFingerprint';
import { getLyricsWithMeta, LyricsResult } from './lyricsService';

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
  related: 15 * 60,     // 15 minutes
  lyrics: 30 * 24 * 60 * 60, // 30 days (synced)
  lyricsPlain: 7 * 24 * 60 * 60, // 7 days (plain-only)
  lyricsNone: 6 * 60 * 60, // 6 hours (nothing found)
} as const;

// ─── Cache key prefixes ─────────────────────────────────────────────

const CACHE_PREFIX = {
  search: 'music:search:',
  song: 'music:song:',
  album: 'music:album:',
  artist: 'music:artist:',
  playlist: 'music:playlist:',
  trending: 'music:trending',
  related: 'music:related:',
  lyrics: 'music:lyrics:v3:',
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
  _providers.set(provider.id, provider);
  if (isDefault) {
    _defaultProviderId = provider.id;
  }
  logger.info({ provider: provider.id }, 'Music provider registered');
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
 * Get playlist with songs. Supports both custom user-created playlists (from MongoDB)
 * and external provider playlists (e.g. JioSaavn).
 */
export async function getPlaylist(id: string, providerId?: string): Promise<Playlist | null> {
  // 1. Check if ID is a custom user playlist in MongoDB
  if (Types.ObjectId.isValid(id)) {
    const customCacheKey = `${CACHE_PREFIX.playlist}${id}:custom`;
    const cachedCustom = await getFromCache<Playlist>(customCacheKey);
    if (cachedCustom && (cachedCustom.title || (cachedCustom.songs && cachedCustom.songs.length > 0))) {
      return cachedCustom;
    }

    try {
      const customPlaylist = await PlaylistModel.findById(id).lean();
      if (customPlaylist) {
        let creatorName = 'Synq User';
        try {
          const user = await User.findById(customPlaylist.userId, 'username').lean();
          if (user?.username) {
            creatorName = user.username;
          }
        } catch {}

        const mappedSongs: Song[] = (customPlaylist.songs || []).map((s) => ({
          id: s.songId,
          title: s.title,
          artists: [{ id: '', name: s.artist, imageUrl: null }],
          album: null,
          duration: s.duration || 0,
          imageUrl: upgradeImageUrl(s.imageUrl),
          year: '',
          language: '',
          hasLyrics: false,
          playCount: 0,
          label: '',
          streamRef: s.songId,
          providerId: s.providerId || 'jiosaavn',
        }));

        await storeStreamTokens(mappedSongs);

        const customResult: Playlist = {
          id: customPlaylist._id.toString(),
          title: customPlaylist.name,
          description: customPlaylist.description || `Curated by ${creatorName}`,
          imageUrl: upgradeImageUrl(
            customPlaylist.imageUrl || (mappedSongs.length > 0 ? mappedSongs[0].imageUrl : null),
          ),
          songCount: mappedSongs.length,
          followerCount: 0,
          songs: mappedSongs.map(stripStreamRef),
          providerId: 'custom',
        };

        // Cache custom playlist with 5 minute TTL
        await setCache(customCacheKey, customResult, 5 * 60);
        return customResult;
      }
    } catch (err) {
      logger.error({ err, id }, 'Failed to fetch custom playlist from MongoDB');
    }
  }

  // 2. Fallback to external provider (e.g. JioSaavn)
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.playlist}${id}:${provider.name}`;
  const cached = await getFromCache<Playlist>(cacheKey);
  // Only accept cached value if it is non-empty
  if (cached && (cached.title || (cached.songs && cached.songs.length > 0))) {
    return cached;
  }

  const playlist = await provider.getPlaylist(id);
  if (playlist && (playlist.title || (playlist.songs && playlist.songs.length > 0))) {
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
 * Get homepage data.
 */
export async function getHomeData(providerId?: string): Promise<import('../providers/music').HomeData> {
  const provider = getProvider(providerId);
  const cacheKey = `music:home:${provider.name}`;
  const cached = await getFromCache<import('../providers/music').HomeData>(cacheKey);
  if (cached) return cached;

  const homeData = await provider.getHomeData();
  await setCache(cacheKey, homeData, CACHE_TTL.trending);
  return homeData;
}

/**
 * Get related/recommended songs for a given track,
 * with cover/remix deduplication and stream token storage.
 */
export async function getRelatedSongs(
  songId: string,
  providerId?: string,
  seedTitle?: string,
  seedArtist?: string
): Promise<Song[]> {
  const provider = getProvider(providerId);
  const cacheKey = `${CACHE_PREFIX.related}${songId}:${provider.name}`;
  const cached = await getFromCache<Song[]>(cacheKey);
  if (cached) return cached;

  let candidates: Song[] = [];
  if (typeof provider.getRelatedSongs === 'function') {
    candidates = await provider.getRelatedSongs(songId);
  }

  // Fallback if provider didn't return recommendations: search by artist
  if (candidates.length === 0 && seedArtist) {
    try {
      const searchRes = await provider.search(seedArtist, 1, 15);
      candidates = searchRes.songs;
    } catch {
      candidates = [];
    }
  }

  // Deduplicate against covers, remixes, and duplicate titles
  const excludedIds = new Set<string>([songId]);
  const deduped = deduplicateRelatedSongs(
    candidates,
    seedTitle,
    seedArtist,
    excludedIds,
    (s) => s.id
  );

  await storeStreamTokens(deduped);
  const sanitized = deduped.map(stripStreamRef);

  await setCache(cacheKey, sanitized, CACHE_TTL.related);
  return sanitized;
}

const inflightLyrics = new Map<string, Promise<LyricsResult>>();

/**
 * Get synchronized or plain lyrics for a track.
 */
export async function getLyrics(
  title: string,
  artist: string,
  duration?: number,
  songId?: string,
  providerId?: string
): Promise<LyricsResult> {
  const durSuffix = duration && duration > 0 ? `_${Math.round(duration)}` : '';
  const cacheKey = `${CACHE_PREFIX.lyrics}${hashKey(`${title}_${artist}${durSuffix}`)}`;
  const cached = await getFromCache<LyricsResult>(cacheKey);
  if (cached) return cached;

  const pending = inflightLyrics.get(cacheKey); // coalesce identical in-flight requests
  if (pending) return pending;

  const task = (async () => {
    let provider: MusicProvider | undefined;
    try { provider = getProvider(providerId); } catch { provider = undefined; }
    const p = provider;
    const fallbackGetter = (p && typeof p.getLyrics === 'function' && songId)
      ? () => p.getLyrics!(songId)
      : undefined;

    const { result, cacheable } = await getLyricsWithMeta(title, artist, duration, fallbackGetter);

    if (cacheable) {
      const ttl = result.isSynced ? CACHE_TTL.lyrics
        : result.source === 'none' ? CACHE_TTL.lyricsNone
        : CACHE_TTL.lyricsPlain;
      await setCache(cacheKey, result, ttl);
    }
    return result;
  })().finally(() => inflightLyrics.delete(cacheKey));

  inflightLyrics.set(cacheKey, task);
  return task;
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
