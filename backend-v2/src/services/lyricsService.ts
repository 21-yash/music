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

/**
 * Fetch lyrics from LRCLIB with fallback to JioSaavn plain lyrics.
 */
export async function getLyrics(
  title: string,
  artist: string,
  durationSeconds?: number,
  fallbackPlainGetter?: () => Promise<string | null>
): Promise<LyricsResult> {
  // 1. Try LRCLIB for synchronized lyrics
  try {
    const cleanTitle = title
      .replace(/\s*[\(\[\{].*?[\)\]\}]/g, '')
      .replace(/\s*-\s*.*$/g, '')
      .trim();

    const cleanArtist = artist.split(/[,&]/)[0].trim();

    const params = new URLSearchParams({
      track_name: cleanTitle,
      artist_name: cleanArtist,
    });

    if (durationSeconds && durationSeconds > 0) {
      params.append('duration', Math.round(durationSeconds).toString());
    }

    const url = `https://lrclib.net/api/get?${params.toString()}`;
    const response = await fetch(url, {
      headers: {
        'User-Agent': 'MusicSportsApp/1.0 (https://github.com/21-yash/music)',
        Accept: 'application/json',
      },
    });

    if (response.ok) {
      const data = (await response.json()) as {
        syncedLyrics?: string | null;
        plainLyrics?: string | null;
      };

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

      if (data.plainLyrics && data.plainLyrics.trim().length > 0) {
        return {
          isSynced: false,
          lines: [],
          plainLyrics: data.plainLyrics,
          source: 'lrclib',
        };
      }
    }
  } catch (err) {
    logger.warn({ err, title, artist }, 'LRCLIB fetch failed, trying fallback');
  }

  // 2. Fallback to provider plain text lyrics
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
      logger.warn({ err }, 'Fallback lyrics getter failed');
    }
  }

  return {
    isSynced: false,
    lines: [],
    plainLyrics: '',
    source: 'none',
  };
}
