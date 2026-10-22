import { logger } from '../utils/logger';

export interface LyricLine {
  timeMs: number;
  text: string;
}

export interface LyricsResult {
  isSynced: boolean;
  lines: LyricLine[];
  plainLyrics: string;
  source: 'lrclib' | 'jiosaavn' | 'none';
}

const LRCLIB_USER_AGENT = 'MusicSportsApp/1.0 (https://github.com/21-yash/music)';

interface LrclibResponse {
  id?: number;
  name?: string;
  trackName?: string;
  artistName?: string;
  albumName?: string;
  duration?: number;
  instrumental?: boolean;
  plainLyrics?: string | null;
  syncedLyrics?: string | null;
}

/**
 * Split artist string into individual clean artist names.
 * Supports comma, semicolon, ampersand, slash, and feat/ft keywords.
 */
export function extractArtistNames(artistStr?: string): string[] {
  if (!artistStr || !artistStr.trim()) return [];
  const raw = artistStr.split(/[,;&\/]|\b(?:feat|ft|featuring|with)\.?/i);
  const result: string[] = [];
  for (const part of raw) {
    const cleaned = part.replace(/^[.\s]+|[.\s]+$/g, '').trim();
    if (cleaned.length > 0 && !/^(feat|ft|featuring|with)$/i.test(cleaned)) {
      if (!result.some(existing => existing.toLowerCase() === cleaned.toLowerCase())) {
        result.push(cleaned);
      }
    }
  }
  return result;
}

/**
 * Parse standard LRC format string into structured LyricLine array.
 * Example input:
 * [00:12.34] Line one
 * [00:16.80] Line two
 */
export function parseLrc(lrcText: string): LyricLine[] {
  if (!lrcText) return [];

  const lines = lrcText.split('\n');
  const result: LyricLine[] = [];
  const timeRegex = /\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g;

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) continue;

    // Find all timestamps on this line
    const matches = Array.from(trimmed.matchAll(timeRegex));
    if (matches.length === 0) continue;

    // Text is everything after the last timestamp
    const text = trimmed.replace(timeRegex, '').trim();

    for (const match of matches) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseInt(match[2], 10);
      const millisPart = match[3] || '0';
      const millis = parseInt(millisPart.padEnd(3, '0').slice(0, 3), 10);

      const timeMs = minutes * 60 * 1000 + seconds * 1000 + millis;
      result.push({ timeMs, text });
    }
  }

  // Sort chronologically
  return result.sort((a, b) => a.timeMs - b.timeMs);
}

async function fetchFromLrclib<T>(url: string, timeoutMs = 4500): Promise<T | null> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(url, {
      headers: {
        'User-Agent': LRCLIB_USER_AGENT,
        Accept: 'application/json',
      },
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch (err) {
    return null;
  }
}

function scoreCandidate(
  candidate: LrclibResponse,
  targetTitle: string,
  artists: string[],
  durationSeconds?: number
): number {
  let score = 0;
  const candDuration = candidate.duration || 0;
  const candTrack = (candidate.trackName || candidate.name || '').toLowerCase();
  const candAlbum = (candidate.albumName || '').toLowerCase();
  const candArtist = (candidate.artistName || '').toLowerCase();
  const targetLower = targetTitle.toLowerCase();

  // 1. Prioritize synced lyrics
  if (candidate.syncedLyrics && candidate.syncedLyrics.trim().length > 0) {
    score += 25;
  } else if (candidate.plainLyrics && candidate.plainLyrics.trim().length > 0) {
    score += 5;
  }

  // 2. Duration matching (acoustic fingerprint)
  if (durationSeconds && durationSeconds > 0 && candDuration > 0) {
    const diff = Math.abs(candDuration - durationSeconds);
    if (diff <= 1) {
      score += 20;
    } else if (diff <= 2) {
      score += 15;
    } else if (diff <= 3) {
      score += 10;
    } else if (diff <= 5) {
      score += 3;
    } else if (diff > 8) {
      score -= 30; // heavy penalty for wrong edit/version
    }
  }

  // 3. Artist matches
  let artistMatched = false;
  for (const a of artists) {
    const aLower = a.toLowerCase();
    if (aLower && candArtist.includes(aLower)) {
      score += artistMatched ? 5 : 15;
      artistMatched = true;
    }
  }

  // 4. Track title match
  if (candTrack === targetLower) {
    score += 15;
  } else if (candTrack.includes(targetLower) || targetLower.includes(candTrack)) {
    score += 10;
  }

  // 5. Title-as-Album Fallback:
  // When the app's song title matches LRCLIB's album name (e.g., 'Naal Nachna Eclipsa Audio')
  if (candAlbum === targetLower) {
    score += 25; // Exact match: our song title was LRCLIB's album name
  } else if (candAlbum.includes(targetLower) || targetLower.includes(candAlbum)) {
    score += 15;
  }

  return score;
}

/**
 * Fetch lyrics from LRCLIB with multi-artist iteration, search scoring,
 * title-as-album fallback, and JioSaavn plain lyrics fallback.
 */
export async function getLyrics(
  title: string,
  artist: string,
  durationSeconds?: number,
  fallbackPlainGetter?: () => Promise<string | null>
): Promise<LyricsResult> {
  const rawTitle = title.trim();
  const artists = extractArtistNames(artist);

  let bestPlainCandidate: string | null = null;

  // ─────────────────────────────────────────────────────────────
  // Tier 1: Fast Direct Lookup (/api/get) with each artist candidate
  // Uses the exact title as-is (e.g. (From "Dhurandhar"), official video, etc.)
  // ─────────────────────────────────────────────────────────────
  try {
    const artistsToTry = artists.slice(0, 4);
    for (const candArtist of artistsToTry) {
      const params = new URLSearchParams({
        track_name: rawTitle,
        artist_name: candArtist,
      });

      if (durationSeconds && durationSeconds > 0) {
        params.append('duration', Math.round(durationSeconds).toString());
      }

      const directUrl = `https://lrclib.net/api/get?${params.toString()}`;
      const data = await fetchFromLrclib<LrclibResponse>(directUrl);

      if (data) {
        if (data.syncedLyrics && data.syncedLyrics.trim().length > 0) {
          const lines = parseLrc(data.syncedLyrics);
          if (lines.length > 0) {
            return {
              isSynced: true,
              lines,
              plainLyrics: data.plainLyrics || data.syncedLyrics,
              source: 'lrclib',
            };
          }
        }
        if (data.plainLyrics && !bestPlainCandidate) {
          bestPlainCandidate = data.plainLyrics;
        }
      }
    }
  } catch (err) {
    logger.warn({ err, title, artist }, 'LRCLIB direct fetch error, proceeding to search');
  }

  // ─────────────────────────────────────────────────────────────
  // Tier 2: Search by Track Name with Duration & Artist Scoring
  // ─────────────────────────────────────────────────────────────
  try {
    const searchUrl = `https://lrclib.net/api/search?track_name=${encodeURIComponent(rawTitle)}`;
    let items = await fetchFromLrclib<LrclibResponse[]>(searchUrl);

    // ─────────────────────────────────────────────────────────────
    // Tier 3: Title-as-Album Fallback Search (q=songTitle)
    // If track_name search missed, search across album & track fields.
    // This allows songs where the title in the app is LRCLIB's album name
    // (e.g., app title: 'Naal Nachna Eclipsa Audio' -> LRCLIB album: 'Naal Nachna Eclipsa Audio')
    // ─────────────────────────────────────────────────────────────
    if (!items || items.length === 0) {
      const broadUrl = `https://lrclib.net/api/search?q=${encodeURIComponent(rawTitle)}`;
      items = await fetchFromLrclib<LrclibResponse[]>(broadUrl);
    }

    if (items && Array.isArray(items) && items.length > 0) {
      const scoredItems = items
        .map(item => ({ item, score: scoreCandidate(item, rawTitle, artists, durationSeconds) }))
        .sort((a, b) => b.score - a.score);

      const top = scoredItems[0];
      if (top && top.score >= 25 && top.item.syncedLyrics && top.item.syncedLyrics.trim().length > 0) {
        const lines = parseLrc(top.item.syncedLyrics);
        if (lines.length > 0) {
          return {
            isSynced: true,
            lines,
            plainLyrics: top.item.plainLyrics || top.item.syncedLyrics,
            source: 'lrclib',
          };
        }
      }

      if (top && top.item.plainLyrics && !bestPlainCandidate) {
        bestPlainCandidate = top.item.plainLyrics;
      }
    }
  } catch (err) {
    logger.warn({ err, title }, 'LRCLIB search error');
  }

  // If plain lyrics were found during LRCLIB queries, return them
  if (bestPlainCandidate && bestPlainCandidate.trim().length > 0) {
    return {
      isSynced: false,
      lines: [],
      plainLyrics: bestPlainCandidate,
      source: 'lrclib',
    };
  }

  // ─────────────────────────────────────────────────────────────
  // Tier 4: Provider Plain Text Lyrics Fallback (JioSaavn / YouTube)
  // ─────────────────────────────────────────────────────────────
  if (fallbackPlainGetter) {
    try {
      const plain = await fallbackPlainGetter();
      if (plain && plain.trim().length > 0) {
        return {
          isSynced: false,
          lines: [],
          plainLyrics: plain,
          source: 'jiosaavn',
        };
      }
    } catch (err) {
      logger.warn({ err }, 'Fallback plain lyrics getter failed');
    }
  }

  return {
    isSynced: false,
    lines: [],
    plainLyrics: '',
    source: 'none',
  };
}
