import { MusicProvider } from '../MusicProvider';
import { Song, Album, Artist, Playlist, StreamInfo, SearchResults } from '../types';
import { logger } from '../../../utils/logger';
import { JioSaavnProvider } from '../jiosaavn/jiosaavnProvider';

/**
 * Spotify provider.
 *
 * Uses the Spotify Web API for metadata (search, track details).
 * Stream resolution uses a hybrid engine: searches JioSaavn for matching
 * audio since Spotify doesn't expose raw audio streams via their API.
 *
 * NOTE: This provider is currently NOT the active provider. JioSaavnProvider
 * is used directly for both search and playback (see server.ts).
 * This file is kept for potential future use as a metadata-enrichment source.
 */

/** Reuse a single JioSaavn instance for stream resolution instead of creating one per call. */
const jiosaavnInstance = new JioSaavnProvider();

export class SpotifyProvider implements MusicProvider {
  readonly name = 'Spotify';
  readonly id = 'spotify';

  private accessToken: string | null = null;
  private tokenExpiresAt: number = 0;

  private async getAccessToken(): Promise<string> {
    if (this.accessToken && Date.now() < this.tokenExpiresAt) {
      return this.accessToken;
    }

    const clientId = process.env.SPOTIFY_CLIENT_ID;
    const clientSecret = process.env.SPOTIFY_CLIENT_SECRET;

    if (!clientId || !clientSecret) {
      throw new Error('Spotify credentials missing in .env');
    }

    const response = await fetch('https://accounts.spotify.com/api/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + Buffer.from(clientId + ':' + clientSecret).toString('base64'),
      },
      body: new URLSearchParams({ grant_type: 'client_credentials' }),
      signal: AbortSignal.timeout(10_000),
    });

    if (!response.ok) {
      throw new Error('Failed to fetch Spotify access token');
    }

    const data: any = await response.json();
    this.accessToken = data.access_token;
    // Buffer of 60 seconds before expiration
    this.tokenExpiresAt = Date.now() + (data.expires_in - 60) * 1000;

    return this.accessToken || '';
  }

  private async fetchApi(endpoint: string, params: URLSearchParams = new URLSearchParams()): Promise<any> {
    const token = await this.getAccessToken();
    const url = `https://api.spotify.com/v1${endpoint}?${params.toString()}`;

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(10_000),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => 'No error body');
      logger.error({ status: response.status, body: errorBody, url }, 'Spotify API error details');
      throw new Error(`Spotify API error: ${response.status} ${response.statusText}`);
    }

    return response.json();
  }

  async search(query: string, page: number, limit: number): Promise<SearchResults> {
    const offset = (page - 1) * limit;
    const spotifyLimit = Math.min(limit, 10); // Spotify restricts limit to 10 for basic API access
    const params = new URLSearchParams({
      q: query,
      type: 'track',
      limit: spotifyLimit.toString(),
      offset: offset.toString(),
    });

    const data = await this.fetchApi('/search', params);
    
    const songs: Song[] = (data.tracks?.items || []).map((track: any) => ({
      id: track.id,
      title: track.name,
      artists: track.artists.map((a: any) => ({ id: a.id, name: a.name, imageUrl: null })),
      album: {
        id: track.album.id,
        title: track.album.name,
        imageUrl: track.album.images[0]?.url || '',
      },
      duration: Math.floor(track.duration_ms / 1000),
      imageUrl: track.album.images[0]?.url || '',
      year: track.album.release_date?.substring(0, 4) || '',
      language: '',
      hasLyrics: false,
      playCount: track.popularity * 1000000, // Approximation — Spotify doesn't expose raw play count
      label: '',
      streamRef: track.id, // Spotify ID used for hybrid stream resolution
      providerId: this.id,
    }));

    return { songs, albums: [], artists: [], totalSongs: data.tracks?.total || songs.length, query };
  }

  async getTrending(): Promise<Song[]> {
    const res = await this.search('top hits', 1, 10);
    return res.songs;
  }

  async getHomeData(): Promise<import('../types').HomeData> {
    return { featuredReleases: [], topPlaylists: [], charts: [], bestOf: [], popularArtists: [] };
  }

  async getSong(id: string): Promise<Song | null> {
    try {
      const track = await this.fetchApi(`/tracks/${id}`);
      return {
        id: track.id,
        title: track.name,
        artists: track.artists.map((a: any) => ({ id: a.id, name: a.name, imageUrl: null })),
        album: {
          id: track.album.id,
          title: track.album.name,
          imageUrl: track.album.images[0]?.url || '',
        },
        duration: Math.floor(track.duration_ms / 1000),
        imageUrl: track.album.images[0]?.url || '',
        year: track.album.release_date?.substring(0, 4) || '',
        language: '',
        hasLyrics: false,
        playCount: track.popularity * 1000000,
        label: '',
        streamRef: track.id,
        providerId: this.id,
      };
    } catch {
      return null;
    }
  }

  async getAlbum(_id: string): Promise<Album | null> {
    return null; // TODO: Implement if needed
  }

  async getArtist(_id: string): Promise<Artist | null> {
    return null; // TODO: Implement if needed
  }

  async getPlaylist(_id: string): Promise<Playlist | null> {
    return null; // TODO: Implement if needed
  }

  async getSongsByIds(ids: string[]): Promise<Song[]> {
    const songs = await Promise.all(ids.map(id => this.getSong(id)));
    return songs.filter(s => s !== null) as Song[];
  }

  /**
   * Resolve a stream URL using the hybrid engine.
   *
   * Flow:
   * 1. Fetch track metadata from Spotify
   * 2. Search JioSaavn for a matching song (strict ±15s duration check)
   * 3. If found, decrypt and return JioSaavn's 320kbps CDN URL
   * 4. If not found, throw — YouTube fallback is disabled
   */
  async resolveStreamUrl(streamRef: string, quality: 'high' | 'medium' | 'low'): Promise<StreamInfo> {
    const track = await this.getSong(streamRef);
    if (!track) throw new Error('Spotify track not found');

    const searchQuery = `${track.title} ${track.artists[0]?.name || ''}`;

    // Try JioSaavn for 320kbps high-quality audio
    try {
      const jsResults = await jiosaavnInstance.search(searchQuery, 1, 5);
      
      if (jsResults.songs.length > 0) {
        // Strict matching: duration must be within 15 seconds of original Spotify track
        const bestMatch = jsResults.songs.find(s => Math.abs(s.duration - track.duration) <= 15);

        if (bestMatch) {
          const jsStream = await jiosaavnInstance.resolveStreamUrl(bestMatch.streamRef, quality);
          logger.info({ spotifyTrack: track.title, jiosaavnId: bestMatch.id }, 'Hybrid Stream: JioSaavn Match Found');
          
          if (jsStream.type === 'jiosaavn') {
            return {
              ...jsStream,
              quality: `${jsStream.quality} (JioSaavn Engine)`
            };
          } else {
            return jsStream;
          }
        } else {
          logger.info('Hybrid Stream: JioSaavn returned results but none matched the strict duration. Skipping.');
        }
      }
    } catch (err) {
      logger.warn({ error: err }, 'Hybrid Stream: JioSaavn failed');
    }

    // Fallback: Search YouTube Music and return the videoId for client-side extraction
    try {
      const YTMusic = require('ytmusic-api').default;
      const ytmusic = new YTMusic();
      await ytmusic.initialize();
      const ytResults = await ytmusic.searchSongs(searchQuery);

      if (ytResults && ytResults.length > 0) {
        return {
          type: 'youtube',
          videoId: ytResults[0].videoId
        };
      }
    } catch (err) {
      logger.error({ error: err }, 'Hybrid Stream: YTMusic search failed');
    }

    return {
      type: 'none',
      message: 'Song not available on JioSaavn and could not be found on YouTube.'
    };
  }
}
