import type { Song, ArtistCredit, AlbumRef } from '../types';
import type { JioSaavnRawSong } from './jiosaavnTypes';

/**
 * Maps raw JioSaavn API data to normalized app types.
 *
 * Responsibilities:
 * - Clean HTML entities from text fields
 * - Extract artist credits from the various JioSaavn response formats
 * - Upgrade image URLs to high resolution
 * - Store encrypted_media_url as the opaque streamRef
 */

const PROVIDER_ID = 'jiosaavn';

// ─── Text cleanup ────────────────────────────────────────────────────

const HTML_ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#039;': "'",
  '&apos;': "'",
};

const ENTITY_REGEX = /&(?:amp|lt|gt|quot|#039|apos);/g;

export function cleanText(text: string | undefined | null): string {
  if (!text) return '';
  return text.replace(ENTITY_REGEX, (match) => HTML_ENTITIES[match] ?? match);
}

// ─── Image URL ───────────────────────────────────────────────────────

/**
 * Upgrade JioSaavn image to 500x500.
 */
export function upgradeImageUrl(url: string | undefined | null): string | null {
  if (!url) return null;
  return url
    .replace(/\/150x150\b/g, '/500x500')
    .replace(/\/50x50\b/g, '/500x500');
}

// ─── Artist extraction ──────────────────────────────────────────────

/**
 * Extract artist credits from the various formats JioSaavn uses.
 *
 * Priority: artistMap.primary_artists > primary_artists string > singers > music
 */
export function extractArtists(raw: JioSaavnRawSong): ArtistCredit[] {
  // Structured artist data (best case)
  if (raw.artistMap?.primary_artists?.length) {
    return raw.artistMap.primary_artists.map((a) => ({
      id: a.id,
      name: cleanText(a.name),
      imageUrl: upgradeImageUrl(a.image),
    }));
  }

  // Comma-separated string fallback
  const artistString = raw.primary_artists || raw.singers || raw.music || '';
  if (!artistString) return [];

  return cleanText(artistString)
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean)
    .map((name) => ({
      id: '', // No ID available from string format
      name,
      imageUrl: null,
    }));
}

// ─── Album extraction ───────────────────────────────────────────────

export function extractAlbumRef(raw: JioSaavnRawSong): AlbumRef | null {
  if (!raw.album && !raw.albumid) return null;

  const albumTitle =
    typeof raw.album === 'string'
      ? raw.album
      : raw.album?.name ?? '';

  const albumId =
    raw.albumid ??
    (typeof raw.album === 'object' ? raw.album?.id : '') ??
    '';

  if (!albumTitle && !albumId) return null;

  return {
    id: albumId,
    title: cleanText(albumTitle),
    imageUrl: upgradeImageUrl(raw.image), // Song image is typically the album art
  };
}

// ─── Song mapping ───────────────────────────────────────────────────

/**
 * Map a raw JioSaavn song object to a normalized Song.
 *
 * Returns null if the song has no playable media URL.
 */
export function mapSong(raw: JioSaavnRawSong): Song | null {
  if (!raw) return null;

  // Must have a media URL to be playable
  if (!raw.encrypted_media_url && !raw.media_preview_url) return null;

  const title = cleanText(raw.song || raw.title || raw.name || '');
  if (!title) return null;

  return {
    id: raw.id,
    title,
    artists: extractArtists(raw),
    album: extractAlbumRef(raw),
    duration: parseInt(String(raw.duration || '0'), 10),
    imageUrl: upgradeImageUrl(raw.image),
    year:
      raw.year || (raw.release_date ? raw.release_date.split('-')[0] : '') || '',
    language: raw.language || '',
    hasLyrics: raw.has_lyrics === 'true' || raw.has_lyrics === true,
    playCount: parseInt(String(raw.play_count || '0'), 10) || 0,
    label: cleanText(raw.label),
    streamRef: raw.encrypted_media_url || raw.media_preview_url || '',
    providerId: PROVIDER_ID,
  };
}

// ─── Deduplication & scoring ────────────────────────────────────────

/**
 * Patterns indicating a non-original variant that should be ranked lower.
 */
const VARIANT_PATTERNS = [
  /\b(karaoke|instrumental|cover|remix|lofi|lo-fi|reverb|slowed)\b/i,
  /\b(8d\s*audio|reverb\s*\+\s*slowed|bass\s*boosted)\b/i,
  /\b(unplugged|acoustic\s*version|live\s*version)\b/i,
  /\b(reprise|revisited|recreated|remastered)\b/i,
  /\blofi\s*mix\b/i,
  /\bslowed\s*\+\s*reverb\b/i,
];

/**
 * Generate a fingerprint for deduplication.
 *
 * Normalizes the title by:
 * - Lowercasing
 * - Removing parenthetical suffixes like "(From ...)" or "(Official Video)"
 * - Removing all non-alphanumeric characters
 * - Using the first artist name only (covers often have different artists)
 *
 * Two songs with the same fingerprint are considered duplicates.
 */
export function songFingerprint(song: Song): string {
  let title = song.title.toLowerCase();

  // Strip parenthetical info: (From "Movie"), (Official Video), etc.
  title = title.replace(/\s*\(.*?\)\s*/g, ' ');
  // Strip bracket info: [Official Audio], etc.
  title = title.replace(/\s*\[.*?\]\s*/g, ' ');
  // Remove non-alphanumeric
  title = title.replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();

  // Use first artist for fingerprint (ignore featured artists)
  const artist = (song.artists[0]?.name || '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');

  // Round duration to nearest 5 seconds to handle rounding differences
  // between autocomplete and search results for the same song
  const roundedDuration = Math.round(song.duration / 5) * 5;

  return `${title}|${artist}|${roundedDuration}`;
}

/**
 * Score a song for ranking.
 *
 * Higher score = should appear first.
 *
 * Factors:
 * - Play count (log scale — a 10M play count song scores much higher than 10K)
 * - Whether the artist has an ID (indicates a known/verified artist)
 * - Whether the title matches a variant pattern (penalized)
 * - Song has album info (original releases usually do)
 * - Has lyrics
 */
export function scoreSong(song: Song): number {
  let score = 0;

  // Play count: log10 scale, capped contribution
  // 1M plays → 6, 100K → 5, 10K → 4, 1K → 3
  if (song.playCount > 0) {
    score += Math.min(Math.log10(song.playCount), 10);
  }

  // Artist with a real ID (from artistMap) — likely a known artist
  if (song.artists[0]?.id) {
    score += 2;
  }

  // Has album info — original releases almost always have this
  if (song.album?.id) {
    score += 1;
  }

  // Has lyrics
  if (song.hasLyrics) {
    score += 0.5;
  }

  // Variant penalty — covers, karaoke, remixes rank lower
  const isVariant = VARIANT_PATTERNS.some((p) => p.test(song.title));
  if (isVariant) {
    score -= 5;
  }

  return score;
}

/**
 * Deduplicate and rank a list of songs.
 *
 * For each group of duplicates (same fingerprint), keeps the one with
 * the highest score. Then sorts the final list by score descending.
 */
export function deduplicateAndRank(songs: Song[]): Song[] {
  const seen = new Map<string, { song: Song; score: number }>();

  for (const song of songs) {
    const fp = songFingerprint(song);
    const sc = scoreSong(song);

    const existing = seen.get(fp);
    if (!existing || sc > existing.score) {
      seen.set(fp, { song, score: sc });
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => b.score - a.score)
    .map((entry) => entry.song);
}
