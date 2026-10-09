/**
 * Normalized music types.
 *
 * These are the app-owned models that every music provider must map to.
 * Neither the Android client nor the rest of the backend should ever see
 * a third-party API's raw response shape — only these types.
 *
 * Design note: all IDs are strings (even if the upstream provider uses
 * numbers) so the interface stays provider-agnostic. The `providerId`
 * field records which provider the data came from, enabling future
 * multi-provider scenarios.
 */

// ─── Core types ──────────────────────────────────────────────────────

export interface Song {
  id: string;
  title: string;
  artists: ArtistCredit[];
  album: AlbumRef | null;
  duration: number; // seconds
  imageUrl: string | null;
  year: string;
  language: string;
  hasLyrics: boolean;
  playCount: number;
  label: string;
  /**
   * Opaque reference the provider uses to resolve the actual stream URL.
   * Never exposed to the client — only used server-side by the stream endpoint.
   */
  streamRef: string;
  providerId: string;
}

export interface Album {
  id: string;
  title: string;
  artists: ArtistCredit[];
  imageUrl: string | null;
  year: string;
  language: string;
  songCount: number;
  songs: Song[];
  providerId: string;
}

export interface Artist {
  id: string;
  name: string;
  imageUrl: string | null;
  bio: string;
  followerCount: number;
  topSongs: Song[];
  albums: AlbumRef[];
  providerId: string;
}

export interface Playlist {
  id: string;
  title: string;
  description: string;
  imageUrl: string | null;
  songCount: number;
  followerCount: number;
  songs: Song[];
  providerId: string;
}

// ─── Reference types (lightweight, for embedding) ────────────────────

/** Lightweight artist reference embedded in songs/albums. */
export interface ArtistCredit {
  id: string;
  name: string;
  imageUrl: string | null;
}

/** Lightweight album reference embedded in songs. */
export interface AlbumRef {
  id: string;
  title: string;
  imageUrl: string | null;
}

// ─── Search ──────────────────────────────────────────────────────────

export interface SearchResults {
  songs: Song[];
  albums: AlbumRef[];
  artists: ArtistCredit[];
  /** Total number of song results available (for pagination). */
  totalSongs: number;
  query: string;
}

// ─── Stream ──────────────────────────────────────────────────────────

export type StreamInfo = 
  | { type: 'jiosaavn'; url: string; quality: string; contentType: string }
  | { type: 'youtube'; videoId: string }
  | { type: 'none'; message: string };

// ─── Homepage ────────────────────────────────────────────────────────

export interface PlaylistRef {
  id: string;
  title: string;
  imageUrl: string | null;
}

export interface HomeData {
  featuredReleases: AlbumRef[];
  topPlaylists: PlaylistRef[];
  charts: PlaylistRef[];
  bestOf: PlaylistRef[];
}
