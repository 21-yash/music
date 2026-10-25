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
 * Curated list of popular artists with permanent JioSaavn CDN images.
 * Includes Bollywood icons, golden playback legends, modern hitmakers, and global stars.
 */
export const POPULAR_ARTISTS = [
  // Top Bollywood & Indian Melodies
  { id: "459320", name: "Arijit Singh", imageUrl: "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg" },
  { id: "456323", name: "Pritam", imageUrl: "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg" },
  { id: "455782", name: "KK", imageUrl: "https://c.saavncdn.com/artists/KK_500x500.jpg" },
  { id: "455124", name: "Mohit Chauhan", imageUrl: "https://c.saavncdn.com/artists/Mohit_Chauhan_500x500.jpg" },
  { id: "455125", name: "Sonu Nigam", imageUrl: "https://c.saavncdn.com/artists/Sonu_Nigam_003_20260813182013_500x500.jpg" },
  { id: "455135", name: "Shaan", imageUrl: "https://c.saavncdn.com/artists/Shaan_006_20261012091314_500x500.jpg" },
  { id: "455130", name: "Shreya Ghoshal", imageUrl: "https://c.saavncdn.com/artists/Shreya_Ghoshal_007_20241101074144_500x500.jpg" },
  { id: "455129", name: "Sunidhi Chauhan", imageUrl: "https://c.saavncdn.com/artists/Sunidhi_Chauhan_005_20250515061617_500x500.jpg" },
  { id: "456269", name: "A.R. Rahman", imageUrl: "https://c.saavncdn.com/artists/AR_Rahman_002_20210120084455_500x500.jpg" },
  
  // Golden Playback Legends
  { id: "455144", name: "Kishore Kumar", imageUrl: "https://c.saavncdn.com/artists/Kishore_Kumar_500x500.jpg" },
  { id: "505312", name: "Mohammed Rafi", imageUrl: "https://c.saavncdn.com/artists/Mohammed_Rafi_500x500.jpg" },
  { id: "455109", name: "Lata Mangeshkar", imageUrl: "https://c.saavncdn.com/artists/Lata_Mangeshkar_004_20230623105323_500x500.jpg" },
  { id: "455142", name: "Kumar Sanu", imageUrl: "https://c.saavncdn.com/artists/Kumar_Sanu_500x500.jpg" },
  { id: "455127", name: "Udit Narayan", imageUrl: "https://c.saavncdn.com/artists/Udit_Narayan_004_20241029065120_500x500.jpg" },

  // Composers & Modern Hits
  { id: "459880", name: "Vishal-Shekhar", imageUrl: "https://c.saavncdn.com/artists/Vishal-Shekhar_20191130071357_500x500.jpg" },
  { id: "457145", name: "Salim Merchant", imageUrl: "https://c.saavncdn.com/artists/Salim_Merchant_002_20231030074556_500x500.jpg" },
  { id: "461968", name: "Sachin-Jigar", imageUrl: "https://c.saavncdn.com/artists/Sachin_Jigar_003_20251222093820_500x500.jpg" },
  { id: "3623110", name: "Sachet Tandon", imageUrl: "https://c.saavncdn.com/artists/Sachet_Tandon_20191129105330_500x500.jpg" },
  { id: "531639", name: "Neeti Mohan", imageUrl: "https://c.saavncdn.com/artists/Neeti_Mohan_009_20260821092757_500x500.jpg" },
  { id: "456863", name: "Badshah", imageUrl: "https://c.saavncdn.com/artists/Badshah_006_20241118064015_500x500.jpg" },
  { id: "697691", name: "Karan Aujla", imageUrl: "https://c.saavncdn.com/artists/Karan_Aujla_005_20260925061936_500x500.jpg" },
  { id: "455663", name: "Anirudh Ravichander", imageUrl: "https://c.saavncdn.com/artists/Anirudh_Ravichander_003_20260121134149_500x500.jpg" },

  // Top Global Artists
  { id: "615155", name: "The Weeknd", imageUrl: "https://c.saavncdn.com/artists/The_Weeknd_002_20241003071400_500x500.jpg" },
  { id: "565990", name: "Taylor Swift", imageUrl: "https://c.saavncdn.com/artists/Taylor_Swift_003_20200226074119_500x500.jpg" },
  { id: "512453", name: "Drake", imageUrl: "https://c.saavncdn.com/artists/Drake_006_20260520062317_500x500.jpg" },
  { id: "1918741", name: "Billie Eilish", imageUrl: "https://c.saavncdn.com/artists/Billie_Eilish_20190211151539_500x500.jpg" }
];

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
      let artistId = id.trim();
      // If id is not numeric (e.g. an artist name), look up the real JioSaavn artist ID
      if (!/^\d+$/.test(artistId)) {
        try {
          const searchParams = new URLSearchParams({
            __call: 'search.getArtistResults',
            _format: 'json',
            _marker: '0',
            cc: 'in',
            q: artistId,
          });
          const searchData = await this.fetch<{ results?: Array<{ id: string; name: string }> }>(searchParams);
          if (searchData?.results?.[0]?.id) {
            artistId = String(searchData.results[0].id);
          }
        } catch (searchErr) {
          logger.warn({ error: searchErr, query: artistId }, 'JioSaavn: failed to search artist by name');
        }
      }

      const params = new URLSearchParams({
        __call: 'artist.getArtistPageDetails',
        _format: 'json',
        _marker: '0',
        cc: 'in',
        artistId: artistId,
        n_song: '20',
        n_album: '10',
      });

      const data = await this.fetch<JioSaavnArtistResponse>(params);
      
      const rawTopSongs = (data.topSongs as any)?.songs || data.topSongs || [];
      const seenTitles = new Set<string>();
      const topSongs = (Array.isArray(rawTopSongs) ? rawTopSongs : [])
        .map(mapSong)
        .filter((s): s is Song => s !== null)
        .filter(s => {
          const cleanTitle = s.title
            .toLowerCase()
            .replace(/\(.*?\)/g, '')
            .replace(/\[.*?\]/g, '')
            .replace(/-.*/g, '')
            .replace(/[^a-z0-9]/g, '');
            
          if (seenTitles.has(cleanTitle)) return false;
          seenTitles.add(cleanTitle);
          return true;
        });

      const rawTopAlbums = (data.topAlbums as any)?.albums || data.topAlbums || [];
      const albums: AlbumRef[] = (Array.isArray(rawTopAlbums) ? rawTopAlbums : []).map((a) => ({
        id: a.albumid || a.id || '',
        title: cleanText(a.album || a.title || a.name || ''),
        imageUrl: upgradeImageUrl(a.imageUrl || a.image),
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

      // If topSongs is empty and artist has a name, use lightweight search.getResults
      if (topSongs.length === 0 && data.name) {
        try {
          const searchParams = new URLSearchParams({
            __call: 'search.getResults',
            _format: 'json',
            _marker: '0',
            cc: 'in',
            q: cleanText(data.name),
            p: '1',
            n: '20',
          });
          const searchData = await this.fetch<JioSaavnSearchResponse>(searchParams);
          const rawSearchSongs = searchData.results || [];
          for (const rawS of rawSearchSongs) {
            const mapped = mapSong(rawS);
            if (!mapped) continue;
            const cleanTitle = mapped.title
              .toLowerCase()
              .replace(/\(.*?\)/g, '')
              .replace(/\[.*?\]/g, '')
              .replace(/-.*/g, '')
              .replace(/[^a-z0-9]/g, '');
            if (!seenTitles.has(cleanTitle)) {
              seenTitles.add(cleanTitle);
              topSongs.push(mapped);
            }
          }
        } catch (searchErr) {
          logger.warn({ error: searchErr, artist: data.name }, 'JioSaavn: fallback song search failed');
        }
      }

      // Fallback artist image if JioSaavn API returned no valid image
      let artistImageUrl = upgradeImageUrl(data.image);
      if (!artistImageUrl) {
        const found = POPULAR_ARTISTS.find(
          (a) => a.id === artistId || a.name.toLowerCase() === cleanText(data.name || '').toLowerCase()
        );
        if (found) {
          artistImageUrl = found.imageUrl;
        }
      }

      return {
        id: data.artistId || data.id || id,
        name: cleanText(data.name || ''),
        imageUrl: artistImageUrl,
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

      const title = cleanText(data.title || data.listname || '');
      if (!title && songs.length === 0) {
        return null;
      }

      return {
        id: data.listid || data.id || id,
        title,
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

      return {
        featuredReleases,
        topPlaylists,
        charts,
        bestOf,
        popularArtists: POPULAR_ARTISTS,
      };
    } catch (error) {
      logger.error({ error }, 'JioSaavn: failed to fetch home data');
      return { featuredReleases: [], topPlaylists: [], charts: [], bestOf: [], popularArtists: [] };
    }
  }

  // ─── Recommendations & Lyrics ───────────────────────────────────

  async getRelatedSongs(songId: string): Promise<Song[]> {
    try {
      const params = new URLSearchParams({
        __call: 'reco.getreco',
        api_version: '4',
        _format: 'json',
        _marker: '0',
        pid: songId,
      });

      const raw = await this.fetch<Record<string, JioSaavnRawSong[]> | JioSaavnRawSong[]>(params);
      let rawList: JioSaavnRawSong[] = [];

      if (Array.isArray(raw)) {
        rawList = raw;
      } else if (raw && typeof raw === 'object') {
        const firstVal = raw[songId] || Object.values(raw)[0];
        if (Array.isArray(firstVal)) {
          rawList = firstVal;
        }
      }

      const mapped = rawList.map(mapSong).filter((s): s is Song => s !== null);
      return deduplicateAndRank(mapped);
    } catch (error) {
      logger.error({ error, songId }, 'JioSaavn: failed to get recommendations');
      return [];
    }
  }

  async getLyrics(songId: string): Promise<string | null> {
    try {
      const params = new URLSearchParams({
        __call: 'lyrics.getLyrics',
        api_version: '4',
        _format: 'json',
        _marker: '0',
        lyrics_id: songId,
      });

      const data = await this.fetch<{ lyrics?: string }>(params);
      return data?.lyrics || null;
    } catch (error) {
      logger.error({ error, songId }, 'JioSaavn: failed to get lyrics');
      return null;
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
