import type { MusicProvider } from '../MusicProvider';
import type {
  Song,
  Album,
  Artist,
  Playlist,
  SearchResults,
  StreamInfo,
  AlbumRef,
  ArtistCredit,
} from '../types';
import type {
  JioSaavnSearchResponse,
  JioSaavnAutocompleteResponse,
  JioSaavnRawSong,
  JioSaavnSongDetailsResponse,
  JioSaavnAlbumResponse,
  JioSaavnArtistResponse,
  JioSaavnPlaylistResponse,
  JioSaavnHomepageResponse,
} from './jiosaavnTypes';
import {
  mapSong,
  deduplicateAndRank,
  cleanText,
  upgradeImageUrl,
} from './jiosaavnMapper';
import {
  decryptMediaUrl,
  getQualityUrl,
  getQualityLabel,
} from './jiosaavnCrypto';
import { logger } from '../../../utils/logger';

const BASE_URL = 'https://www.jiosaavn.com/api.php';

const DEFAULT_HEADERS: Record<string, string> = {
  'User-Agent':
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  Accept: 'application/json',
};

/**
 * JioSaavn music provider.
 *
 * Implements the MusicProvider interface using JioSaavn's internal API.
 *
 * Search strategy:
 * - Runs autocomplete + regular search in parallel
 * - Fetches full details for autocomplete song IDs
 * - Merges, deduplicates (by title+artist+duration fingerprint),
 *   and ranks results using a scoring system that penalizes
 *   covers/karaoke/remixes and prioritizes high play-count originals.
 *
 * No geo-spoofing headers are used — the backend is expected to run
 * in an Asian region (Singapore/Mumbai) where JioSaavn's full catalog
 * is available.
 */
export class JioSaavnProvider implements MusicProvider {
  readonly name = 'JioSaavn';
  readonly id = 'jiosaavn';

  // ─── Search ──────────────────────────────────────────────────────

  async search(
    query: string,
    page: number,
    limit: number,
  ): Promise<SearchResults> {
    // Run autocomplete and regular search in parallel
    const [autocompleteResult, searchResult] = await Promise.allSettled([
      this.fetchAutocomplete(query),
      this.fetchSearch(query, page, limit),
    ]);

    let allSongs: Song[] = [];
    let albums: AlbumRef[] = [];
    let artists: ArtistCredit[] = [];
    let totalSongs = 0;

    // Process autocomplete results — get song IDs and fetch full details
    if (autocompleteResult.status === 'fulfilled') {
      const acData = autocompleteResult.value;

      // Get full song details for autocomplete song IDs
      const songIds = (acData.songs?.data || [])
        .map((s) => s.id)
        .filter(Boolean)
        .slice(0, 10);

      if (songIds.length > 0) {
        const detailedSongs = await this.getSongsByIds(songIds);
        allSongs.push(...detailedSongs);
      }

      // Extract album and artist suggestions
      albums = (acData.albums?.data || []).map((a) => ({
        id: a.id,
        title: cleanText(a.title),
        imageUrl: upgradeImageUrl(a.image),
      }));

      artists = (acData.artists?.data || []).map((a) => ({
        id: a.id,
        name: cleanText(a.title),
        imageUrl: upgradeImageUrl(a.image),
      }));
    }

    // Process regular search results
    if (searchResult.status === 'fulfilled') {
      const searchData = searchResult.value;
      const searchSongs = (searchData.results || [])
        .map(mapSong)
        .filter((s): s is Song => s !== null);
      allSongs.push(...searchSongs);
      totalSongs = searchData.total || searchSongs.length;
    }

    // Deduplicate and rank — this handles covers, variants, duplicates
    const rankedSongs = deduplicateAndRank(allSongs).slice(0, limit);

    return {
      songs: rankedSongs,
      albums,
      artists,
      totalSongs: Math.max(totalSongs, rankedSongs.length),
      query,
    };
  }

  // ─── Song details ────────────────────────────────────────────────

  async getSong(id: string): Promise<Song | null> {
    const songs = await this.getSongsByIds([id]);
    return songs[0] ?? null;
  }

  async getSongsByIds(ids: string[]): Promise<Song[]> {
    if (ids.length === 0) return [];

    try {
      const params = new URLSearchParams({
        __call: 'song.getDetails',
        cc: 'in',
        _marker: '0',
        _format: 'json',
        pids: ids.join(','),
      });

      const data = await this.fetch<JioSaavnSongDetailsResponse>(params);

      // Response is { songs: { id: songData } } or { id: songData }
      const songMap = data.songs ?? data;
      const rawSongs = Object.values(songMap).filter(
        (v): v is JioSaavnRawSong =>
          typeof v === 'object' && v !== null && 'id' in v,
      );

      return rawSongs.map(mapSong).filter((s): s is Song => s !== null);
    } catch (error) {
      logger.error({ error, ids }, 'JioSaavn: failed to fetch song details');
      return [];
    }
  }

  // ─── Album ───────────────────────────────────────────────────────

  async getAlbum(id: string): Promise<Album | null> {
    try {
      const params = new URLSearchParams({
        __call: 'content.getAlbumDetails',
        _format: 'json',
        _marker: '0',
        cc: 'in',
        albumid: id,
      });

      const data = await this.fetch<JioSaavnAlbumResponse>(params);
      const songs = (data.list || data.songs || [])
        .map(mapSong)
        .filter((s): s is Song => s !== null);

      const artistString = data.primary_artists || data.subtitle || '';

      return {
        id: data.albumid || data.id || id,
        title: cleanText(data.title || data.name || ''),
        artists: cleanText(artistString)
          .split(',')
          .map((name) => name.trim())
          .filter(Boolean)
          .map((name) => ({ id: '', name, imageUrl: null })),
        imageUrl: upgradeImageUrl(data.image),
        year: data.year || '',
        language: data.language || '',
        songCount: songs.length,
        songs,
        providerId: this.id,
      };
    } catch (error) {
      logger.error({ error, albumId: id }, 'JioSaavn: failed to fetch album');
      return null;
    }
  }

  // ─── Artist ──────────────────────────────────────────────────────

  async getArtist(id: string): Promise<Artist | null> {
    try {
      const params = new URLSearchParams({
        __call: 'artist.getArtistPageDetails',
        _format: 'json',
        _marker: '0',
        cc: 'in',
        artistId: id,
        n_song: '20',
        n_album: '10',
      });

      const data = await this.fetch<JioSaavnArtistResponse>(params);

      const topSongs = (data.topSongs || [])
        .map(mapSong)
        .filter((s): s is Song => s !== null);

      const albums: AlbumRef[] = (data.topAlbums || []).map((a) => ({
        id: a.id,
        title: cleanText(a.title || a.name || ''),
        imageUrl: upgradeImageUrl(a.image),
      }));

      const bio = (data.bio || [])
        .map((b) => cleanText(b.text))
        .filter(Boolean)
        .join(' ');

      return {
        id: data.artistId || data.id || id,
        name: cleanText(data.name || ''),
        imageUrl: upgradeImageUrl(data.image),
        bio,
        followerCount:
          parseInt(String(data.fan_count || data.follower_count || '0'), 10) ||
          0,
        topSongs,
        albums,
        providerId: this.id,
      };
    } catch (error) {
      logger.error({ error, artistId: id }, 'JioSaavn: failed to fetch artist');
      return null;
    }
  }

  // ─── Playlist ────────────────────────────────────────────────────

  async getPlaylist(id: string): Promise<Playlist | null> {
    try {
      const params = new URLSearchParams({
        __call: 'playlist.getDetails',
        _format: 'json',
        _marker: '0',
        cc: 'in',
        listid: id,
      });

      const data = await this.fetch<JioSaavnPlaylistResponse>(params);
      const songs = (data.list || data.songs || [])
        .map(mapSong)
        .filter((s): s is Song => s !== null);

      return {
        id: data.listid || data.id || id,
        title: cleanText(data.title || data.listname || ''),
        description: cleanText(data.subtitle || ''),
        imageUrl: upgradeImageUrl(data.image),
        songCount:
          parseInt(String(data.list_count || '0'), 10) || songs.length,
        followerCount:
          parseInt(
            String(data.fan_count || data.follower_count || '0'),
            10,
          ) || 0,
        songs,
        providerId: this.id,
      };
    } catch (error) {
      logger.error(
        { error, playlistId: id },
        'JioSaavn: failed to fetch playlist',
      );
      return null;
    }
  }

  // ─── Trending ────────────────────────────────────────────────────

  async getTrending(): Promise<Song[]> {
    try {
      // Try homepage data first
      const params = new URLSearchParams({
        __call: 'content.getHomepageData',
        _format: 'json',
        _marker: '0',
        ctx: 'web6dot0',
      });

      const data = await this.fetch<JioSaavnHomepageResponse>(params);
      let songs: Song[] = [];

      // Try new_trending section
      if (data.new_trending) {
        songs = data.new_trending
          .filter((item) => item.type === 'song')
          .map(mapSong)
          .filter((s): s is Song => s !== null);
      }

      // Fallback: search for trending songs
      if (songs.length === 0) {
        const searchParams = new URLSearchParams({
          __call: 'search.getResults',
          _format: 'json',
          _marker: '0',
          cc: 'in',
          q: 'trending hindi songs 2025',
          p: '1',
          n: '30',
        });

        const searchData =
          await this.fetch<JioSaavnSearchResponse>(searchParams);
        songs = (searchData.results || [])
          .map(mapSong)
          .filter((s): s is Song => s !== null);
      }

      return deduplicateAndRank(songs).slice(0, 30);
    } catch (error) {
      logger.error({ error }, 'JioSaavn: failed to fetch trending');
      return [];
    }
  }

  // ─── Stream URL resolution ───────────────────────────────────────

  async resolveStreamUrl(
    streamRef: string,
    quality: 'high' | 'medium' | 'low',
  ): Promise<StreamInfo> {
    const decrypted = decryptMediaUrl(streamRef);
    if (!decrypted) {
      throw new Error('Failed to decrypt stream URL');
    }

    const url = getQualityUrl(decrypted, quality);

    return {
      url,
      quality: getQualityLabel(quality),
      contentType: 'audio/mp4',
    };
  }

  // ─── Internal fetch helpers ──────────────────────────────────────

  private async fetch<T>(params: URLSearchParams): Promise<T> {
    const url = `${BASE_URL}?${params.toString()}`;

    const response = await fetch(url, {
      headers: DEFAULT_HEADERS,
      signal: AbortSignal.timeout(10_000),
    });

    if (!response.ok) {
      throw new Error(
        `JioSaavn API returned ${response.status}: ${response.statusText}`,
      );
    }

    return (await response.json()) as T;
  }

  private async fetchAutocomplete(
    query: string,
  ): Promise<JioSaavnAutocompleteResponse> {
    const params = new URLSearchParams({
      __call: 'autocomplete.get',
      _format: 'json',
      _marker: '0',
      cc: 'in',
      query,
    });

    return this.fetch<JioSaavnAutocompleteResponse>(params);
  }

  private async fetchSearch(
    query: string,
    page: number,
    limit: number,
  ): Promise<JioSaavnSearchResponse> {
    const params = new URLSearchParams({
      __call: 'search.getResults',
      _format: 'json',
      _marker: '0',
      cc: 'in',
      includeMetaTags: '1',
      ctx: 'web6dot0',
      q: query,
      p: String(page),
      n: String(Math.min(limit * 2, 40)), // Fetch more to compensate for dedup
    });

    return this.fetch<JioSaavnSearchResponse>(params);
  }
}
