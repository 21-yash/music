import { MusicProvider } from '../MusicProvider';
import { Song, Album, Artist, Playlist, SearchResults, StreamInfo } from '../types';
import YTMusic from 'ytmusic-api';

export class YouTubeProvider implements MusicProvider {
  id = 'youtube';
  name = 'YouTube';
  private ytmusic: YTMusic;
  private isInitialized = false;

  constructor() {
    this.ytmusic = new YTMusic();
  }

  private async ensureInitialized() {
    if (!this.isInitialized) {
      await this.ytmusic.initialize();
      this.isInitialized = true;
    }
  }

  async search(query: string, _page: number = 1, limit: number = 20): Promise<SearchResults> {
    await this.ensureInitialized();
    const results = await this.ytmusic.searchSongs(query);

    const songs: Song[] = results.slice(0, limit).map((s: any) => ({
      id: s.videoId,
      title: s.name,
      artists: s.artist ? [{
        id: s.artist.artistId || '',
        name: s.artist.name,
        imageUrl: null
      }] : [],
      album: s.album ? {
        id: s.album.albumId,
        title: s.album.name,
        imageUrl: null
      } : null,
      duration: s.duration,
      imageUrl: s.thumbnails?.[s.thumbnails.length - 1]?.url || null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: s.videoId, // use videoId as the stream reference
      providerId: this.id
    }));

    return {
      songs,
      albums: [],
      artists: [],
      totalSongs: songs.length, // YTMusic API doesn't provide total results easily for generic search
      query
    };
  }

  async getTrending(): Promise<Song[]> {
    return []; // Optional: Could implement using YTMusic charts
  }

  async getHomeData(): Promise<import('../types').HomeData> {
    return { featuredReleases: [], topPlaylists: [], charts: [], bestOf: [] };
  }

  async getSong(id: string): Promise<Song | null> {
    await this.ensureInitialized();
    const song = await this.ytmusic.getSong(id);
    if (!song) return null;
    
    return {
      id: song.videoId,
      title: song.name,
      artists: song.artist ? [{
        id: song.artist.artistId || '',
        name: song.artist.name,
        imageUrl: null
      }] : [],
      album: null,
      duration: 0, // getSong doesn't always return duration in ytmusic-api
      imageUrl: song.thumbnails?.[song.thumbnails.length - 1]?.url || null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: song.videoId,
      providerId: this.id
    };
  }

  async getAlbum(_id: string): Promise<Album | null> {
    return null;
  }

  async getArtist(_id: string): Promise<Artist | null> {
    return null;
  }

  async getPlaylist(_id: string): Promise<Playlist | null> {
    return null;
  }

  async getSongsByIds(_ids: string[]): Promise<Song[]> {
    return [];
  }

  async resolveStreamUrl(streamRef: string, _quality: 'high' | 'medium' | 'low'): Promise<StreamInfo> {
    // For YouTube, we just return the videoId. The Android client will use NewPipeExtractor to resolve the stream.
    return {
      type: 'youtube',
      videoId: streamRef
    };
  }
}
