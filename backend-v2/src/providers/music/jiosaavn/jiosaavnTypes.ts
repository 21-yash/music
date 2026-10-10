/**
 * Raw JioSaavn API response types.
 *
 * These are only used within the JioSaavn provider to type the raw
 * API responses. They are never exposed outside this directory.
 */

// ─── Search results ──────────────────────────────────────────────────

export interface JioSaavnSearchResponse {
  results: JioSaavnRawSong[];
  total: number;
}

export interface JioSaavnAutocompleteResponse {
  songs?: { data: JioSaavnAutocompleteSong[] };
  albums?: { data: JioSaavnAutocompleteAlbum[] };
  artists?: { data: JioSaavnAutocompleteArtist[] };
}

export interface JioSaavnAutocompleteSong {
  id: string;
  title: string;
  description: string; // artist names
  image: string;
  type: string;
}

export interface JioSaavnAutocompleteAlbum {
  id: string;
  title: string;
  description: string;
  image: string;
  type: string;
}

export interface JioSaavnAutocompleteArtist {
  id: string;
  title: string; // artist name in autocomplete
  description: string;
  image: string;
  type: string;
}

// ─── Song details ────────────────────────────────────────────────────

export interface JioSaavnRawSong {
  id: string;
  song?: string;
  title?: string;
  name?: string;
  primary_artists?: string;
  singers?: string;
  music?: string;
  artistMap?: {
    primary_artists?: Array<{ id: string; name: string; image: string }>;
    featured_artists?: Array<{ id: string; name: string; image: string }>;
    artists?: Array<{ id: string; name: string; image: string }>;
  };
  album?: string | { name: string; id: string; url: string };
  albumid?: string;
  duration?: string | number;
  image?: string;
  year?: string;
  release_date?: string;
  language?: string;
  has_lyrics?: string | boolean;
  label?: string;
  play_count?: string | number;
  encrypted_media_url?: string;
  media_preview_url?: string;
  type?: string;
}

// ─── Song details response (by ID) ──────────────────────────────────

export interface JioSaavnSongDetailsResponse {
  songs?: Record<string, JioSaavnRawSong>;
  [key: string]: JioSaavnRawSong | Record<string, JioSaavnRawSong> | undefined;
}

// ─── Album details ───────────────────────────────────────────────────

export interface JioSaavnAlbumResponse {
  id?: string;
  albumid?: string;
  title?: string;
  name?: string;
  image?: string;
  year?: string;
  primary_artists?: string;
  subtitle?: string;
  language?: string;
  list?: JioSaavnRawSong[];
  songs?: JioSaavnRawSong[];
}

// ─── Artist details ──────────────────────────────────────────────────

export interface JioSaavnArtistResponse {
  artistId?: string;
  id?: string;
  name?: string;
  image?: string;
  bio?: Array<{ text: string }>;
  fan_count?: string | number;
  follower_count?: string | number;
  topSongs?: JioSaavnRawSong[] | { songs?: JioSaavnRawSong[] };
  topAlbums?: Array<{
    id?: string;
    albumid?: string;
    title?: string;
    name?: string;
    album?: string;
    image?: string;
    imageUrl?: string;
  }> | { albums?: Array<any> };
}

// ─── Playlist details ────────────────────────────────────────────────

export interface JioSaavnPlaylistResponse {
  id?: string;
  listid?: string;
  title?: string;
  listname?: string;
  subtitle?: string;
  image?: string;
  list?: JioSaavnRawSong[];
  songs?: JioSaavnRawSong[];
  list_count?: string | number;
  fan_count?: string | number;
  follower_count?: string | number;
}

// ─── Homepage / trending ─────────────────────────────────────────────

export interface JioSaavnHomepageResponse {
  new_trending?: JioSaavnRawSong[];
  charts?: Array<any>;
  trending?: { [key: string]: JioSaavnRawSong[] };
  new_albums?: Array<any>;
  featured_playlists?: Array<any>;
}
