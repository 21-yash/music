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

// Use our custom Vercel proxy running in Mumbai (bom1) to bypass geo-blocks
const BASE_URL = 'https://music-jiosaavn-proxy.vercel.app/api/index';

/**
 * Build request headers for the Vercel proxy.
 * Includes the API key for proxy authentication.
 */
function getProxyHeaders(): Record<string, string> {
  return {
    'User-Agent': 'JioSaavn/7.39.2 (Android; 13; en)',
    'app_version': '7.39.2',
    'api_version': '4',
    'readable_version': '7.39.2',
    'network_type': 'WIFI',
    Accept: 'application/json',
    'x-proxy-key': process.env.JIOSAAVN_PROXY_KEY || '',
  };
}

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
 * Geo-spoofing headers are used (X-Forwarded-For) to simulate an Indian
 * IP address so we get access to the full JioSaavn catalog regardless of
 * where the backend is hosted (e.g., Singapore).
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

      let parsedBio: any[] = [];
      if (typeof data.bio === 'string') {
        try { parsedBio = JSON.parse(data.bio); } catch (e) {}
      } else if (Array.isArray(data.bio)) {
        parsedBio = data.bio;
      }

      const bio = parsedBio
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
    } catch (error: any) {
      console.error('getArtist error details:', error.stack || error.message || error);
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
      // Fetch 'Trending Today' playlist (listid: 110858205) for high-quality curated trends
      // This is far more accurate than the generic 'new_trending' homepage section.
      const playlist = await this.getPlaylist('110858205');
      if (playlist && playlist.songs.length > 0) {
        return deduplicateAndRank(playlist.songs).slice(0, 30);
      }

      // Fallback: search for trending songs if playlist fails
      const searchParams = new URLSearchParams({
        __call: 'search.getResults',
        _format: 'json',
        _marker: '0',
        cc: 'in',
        q: `trending hindi songs ${new Date().getFullYear()}`,
        p: '1',
        n: '30',
      });

      const searchData =
        await this.fetch<JioSaavnSearchResponse>(searchParams);
      const songs = (searchData.results || [])
        .map(mapSong)
        .filter((s): s is Song => s !== null);

      return deduplicateAndRank(songs).slice(0, 30);
    } catch (error) {
      logger.error({ error }, 'JioSaavn: failed to fetch trending');
      return [];
    }
  }

  // ─── Homepage Data ───────────────────────────────────────────────

  async getHomeData(): Promise<import('../types').HomeData> {
    try {
      const params = new URLSearchParams({
        __call: 'content.getHomepageData',
        _format: 'json',
        _marker: '0',
        ctx: 'web6dot0',
      });

      const data = await this.fetch<JioSaavnHomepageResponse>(params);

      // Map new_albums -> featuredReleases
      const featuredReleases = (data.new_albums || []).map((a: any) => ({
        id: a.albumid || a.id || '',
        title: cleanText(a.title || a.name || '').replace(/\s*\(.*?\)\s*/g, '').trim(),
        imageUrl: upgradeImageUrl(a.image),
      })).filter((a) => a.id);

      // Map featured_playlists -> topPlaylists
      const allTopPlaylists = (data.featured_playlists || []).map((p: any) => ({
        id: p.listid || p.id || '',
        title: cleanText(p.listname || p.title || '').replace(/\s*\(.*?\)\s*/g, '').trim(),
        imageUrl: upgradeImageUrl(p.image),
      })).filter((p: any) => p.id);

      const bestOf: typeof allTopPlaylists = [];
      const topPlaylists: typeof allTopPlaylists = [];

      for (const p of allTopPlaylists) {
        if (p.title.toLowerCase().includes('best of')) {
          bestOf.push(p);
        } else {
          topPlaylists.push(p);
        }
      }

      // Map charts -> charts
      const charts = (data.charts || []).map((c: any) => ({
        id: c.listid || c.id || '',
        title: cleanText(c.listname || c.title || '').replace(/\s*\(.*?\)\s*/g, '').trim(),
        imageUrl: upgradeImageUrl(c.image),
      })).filter((c: any) => c.id);

      // Hardcoded popular artists (as JioSaavn API doesn't return them directly in homepage)
      const popularArtists = [
        // Top Indian Artists
        { id: "459320", name: "Arijit Singh", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558481813846892596/image.png?ex=6acb98cb&is=6aca474b&hm=870868074f56e73bca959930333f1889bf9e4830d4985f4a0a9e4b867df43f06" },
        { id: "697691", name: "Karan Aujla", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482181578559720/image.png?ex=6acb9923&is=6aca47a3&hm=85fdd7fe42b6e859717dfd7d9a1b455a2273b5ba23ddca80396b37b2c918e7b7&" },
        { id: "456863", name: "Badshah", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482181213388915/image.png?ex=6acb9923&is=6aca47a3&hm=d191a8cc713980df534dd8f90c3a7e1791e98d984e120daa88c9dd22e6d042a1&" },
        { id: "455130", name: "Shreya Ghoshal", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482180785840189/image.png?ex=6acb9923&is=6aca47a3&hm=19ca8192aa5e69f859d835b8e369f6dc420620daee9fc1f5f903161496d1bc04&" },
        { id: "456269", name: "A.R. Rahman", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482180299034674/image.png?ex=6acb9922&is=6aca47a2&hm=94be734d9b9d51286ccabce273625bdb65479359350291eb3bd4f5f791b8c9e2&" },
        { id: "455663", name: "Anirudh Ravichander", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482179900833953/image.png?ex=6acb9922&is=6aca47a2&hm=0351133008ad7f43608bcdfdabb527afd763dd591b028cffecc99b8653eddff8&" },
        
        // Top Global Artists
        { id: "615155", name: "The Weeknd", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482027563712652/image.png?ex=6acb98fe&is=6aca477e&hm=034c3f0da5679f215ba380d68a04fd090b6adf14f66eb0b8e00363e8495621f3&" },
        { id: "565990", name: "Taylor Swift", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482027248877578/image.png?ex=6acb98fe&is=6aca477e&hm=45ebb957e67d53bb68a03a4db2a8d4c7b21152d8d3111fbfb537b61632bab5e7&" },
        { id: "512453", name: "Drake", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482026628251748/image.png?ex=6acb98fe&is=6aca477e&hm=c08e47d5953f82af00b8ea56d41109693bee561756d02cf8c89b9da4d2af8ea6&" },
        { id: "1918741", name: "Billie Eilish", imageUrl: "https://cdn.discordapp.com/attachments/960596190238498901/1558482026196115456/image.png?ex=6acb98fe&is=6aca477e&hm=3ecac8b6cb86a543246f8fa6f4ec85c73635e3b0ccb4c537757e75261804f5ba&" }
      ];

      return {
        featuredReleases,
        topPlaylists,
        charts,
        bestOf,
        popularArtists,
      };
    } catch (error) {
      logger.error({ error }, 'JioSaavn: failed to fetch home data');
      return { featuredReleases: [], topPlaylists: [], charts: [], bestOf: [], popularArtists: [] };
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
      type: 'jiosaavn',
      url,
      quality: getQualityLabel(quality),
      contentType: 'audio/mp4',
    };
  }

  // ─── Internal fetch helpers ──────────────────────────────────────

  private async fetch<T>(params: URLSearchParams): Promise<T> {
    const url = `${BASE_URL}?${params.toString()}`;

    const response = await fetch(url, {
      headers: getProxyHeaders(),
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
