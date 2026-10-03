import { MusicProvider } from '../MusicProvider';
import { Song, Album, Artist, Playlist, StreamInfo, SearchResults } from '../types';
import { logger } from '../../../utils/logger';
import YTMusic from 'ytmusic-api';
import ytdl from '@distube/ytdl-core';
import { JioSaavnProvider } from '../jiosaavn/jiosaavnProvider';

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
      language: '', // Spotify doesn't provide language on tracks easily
      hasLyrics: false,
      playCount: track.popularity * 1000000, // Approximation since Spotify doesn't expose raw play count here
      label: '',
      streamRef: track.id, // We'll pass the Spotify ID to resolveStreamUrl
      providerId: this.id,
    }));

    return { songs, albums: [], artists: [], totalSongs: data.tracks?.total || songs.length, query };
  }

  async getTrending(): Promise<Song[]> {
    // Instead of using playlists (which are heavily restricted and throw 403s), 
    // we'll just search for recent trending hits to populate the home screen.
    const res = await this.search('top hits', 1, 10);
    return res.songs;
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

  async resolveStreamUrl(streamRef: string, quality: 'high' | 'medium' | 'low'): Promise<StreamInfo> {
    // 1. We have the Spotify ID (streamRef)
    // 2. Fetch the exact Track name and Artist from Spotify
    const track = await this.getSong(streamRef);
    if (!track) throw new Error('Spotify track not found');

    const searchQuery = `${track.title} ${track.artists[0]?.name || ''}`;

    // 3. HYBRID ENGINE: Try JioSaavn first for 320kbps High Quality Audio
    try {
      const jiosaavn = new JioSaavnProvider();
      const jsResults = await jiosaavn.search(searchQuery, 1, 5);
      
      if (jsResults.songs.length > 0) {
        const jsSong = jsResults.songs[0];
        // If we found a match, extract the stream from JioSaavn
        const jsStream = await jiosaavn.resolveStreamUrl(jsSong.streamRef, quality);
        logger.info({ spotifyTrack: track.title, jiosaavnId: jsSong.id }, 'Hybrid Stream: JioSaavn Match Found');
        
        // Ensure we add a source marker so the UI knows where it came from
        return {
          ...jsStream,
          quality: `${jsStream.quality} (JioSaavn 320kbps Engine)`
        };
      }
    } catch (err) {
      logger.warn({ error: err }, 'Hybrid Stream: JioSaavn failed, falling back to YouTube');
    }

    // 4. FALLBACK: Search YouTube Music for the exact match
    logger.info({ track: track.title }, 'Hybrid Stream: Using YouTube Fallback');
    const ytmusic = new YTMusic();
    await ytmusic.initialize();
    const ytResults = await ytmusic.searchSongs(searchQuery);

    if (!ytResults || ytResults.length === 0) {
      throw new Error('No equivalent song found on YouTube Music');
    }

    const videoId = ytResults[0].videoId;

    // 4. Extract stream using @distube/ytdl-core
    const info = await ytdl.getInfo(videoId);
    
    // Choose the highest quality audio-only stream
    const audioFormats = ytdl.filterFormats(info.formats, 'audioonly');
    
    if (audioFormats.length === 0) {
      throw new Error('No audio streams found on YouTube');
    }

    // Sort by audio bitrate descending
    audioFormats.sort((a, b) => (b.audioBitrate || 0) - (a.audioBitrate || 0));

    let selectedFormat = audioFormats[0]; // High
    if (quality === 'medium') {
      selectedFormat = audioFormats[Math.floor(audioFormats.length / 2)];
    } else if (quality === 'low') {
      selectedFormat = audioFormats[audioFormats.length - 1];
    }

    return {
      url: selectedFormat.url,
      quality: `${selectedFormat.audioBitrate || 128}kbps (YouTube Fallback)`,
      contentType: selectedFormat.mimeType?.split(';')[0] || 'audio/mp4',
    };
  }
}
