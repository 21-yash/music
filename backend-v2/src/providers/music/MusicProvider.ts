import type {
  Song,
  Album,
  Artist,
  Playlist,
  SearchResults,
  StreamInfo,
  HomeData,
} from './types';

/**
 * Abstract music provider interface.
 *
 * Every external music source (JioSaavn, Spotify, YouTube Music, etc.)
 * must implement this interface. The rest of the application only
 * interacts with music data through these normalized methods —
 * never through raw provider APIs.
 *
 * Design decisions:
 * - Methods return normalized types from `./types.ts`.
 * - The provider is stateless — no user-specific data here.
 *   User library (favorites, playlists) is a separate concern (Phase 6).
 * - `resolveStreamUrl` takes an opaque `streamRef` that the provider
 *   itself produced when returning a Song. This keeps encryption/token
 *   details internal to the provider.
 * - Search quality (deduplication, scoring, original-vs-cover ranking)
 *   is the provider's responsibility, not the service layer's.
 */
export interface MusicProvider {
  /** Human-readable name for logging and debugging. */
  readonly name: string;

  /** Unique identifier used in Song.providerId, etc. */
  readonly id: string;

  /**
   * Search for songs, albums, and artists.
   *
   * The implementation must:
   * - Deduplicate results (same song from different sources/endpoints)
   * - Prioritize originals over covers/karaoke/remixes
   * - Sort by relevance and popularity
   */
  search(query: string, page: number, limit: number): Promise<SearchResults>;

  /** Get full song details by ID. */
  getSong(id: string): Promise<Song | null>;

  /** Get multiple songs by IDs (batch). */
  getSongsByIds(ids: string[]): Promise<Song[]>;

  /** Get album with its songs. */
  getAlbum(id: string): Promise<Album | null>;

  /** Get artist profile with top songs and albums. */
  getArtist(id: string): Promise<Artist | null>;

  /** Get playlist with its songs. */
  getPlaylist(id: string): Promise<Playlist | null>;

  /** Get trending/popular songs. */
  getTrending(): Promise<Song[]>;

  /** Get homepage data (featured releases, playlists, charts) */
  getHomeData(): Promise<HomeData>;

  /**
   * Resolve an opaque stream reference to a playable URL.
   *
   * The `streamRef` was produced by this provider when it returned a Song.
   * The provider handles any decryption, token generation, or URL
   * construction needed to get a direct CDN link.
   *
   * @param streamRef - Opaque reference from Song.streamRef
   * @param quality - Preferred quality ('high' | 'medium' | 'low')
   */
  resolveStreamUrl(
    streamRef: string,
    quality: 'high' | 'medium' | 'low',
  ): Promise<StreamInfo>;
}
