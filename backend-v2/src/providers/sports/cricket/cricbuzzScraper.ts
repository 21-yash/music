/**
 * Cricbuzz RSC (React Server Components) scraper utilities.
 *
 * Cricbuzz uses Next.js with RSC streaming. The data is not available via
 * a public JSON API — it's embedded in the HTML as RSC streaming chunks:
 *
 *   self.__next_f.push([1,"...JSON data..."])
 *
 * This module extracts and parses that data.
 *
 * NOTE: This technique is inherently fragile. Any Cricbuzz frontend refactor
 * can break the extraction. The provider abstraction ensures we can swap to
 * a different data source without touching the rest of the app.
 */

import { logger } from '../../../utils/logger';

// ─── HTTP ────────────────────────────────────────────────────────────

const CRICBUZZ_HEADERS: Record<string, string> = {
  'User-Agent':
    'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1',
  Accept:
    'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.5',
  Referer: 'https://m.cricbuzz.com/',
};

/**
 * Fetch a Cricbuzz mobile page with retry on transient 5xx errors.
 * Returns the raw HTML string.
 */
export async function fetchCricbuzzPage(
  url: string,
  timeoutMs: number = 12_000,
  retries: number = 2,
): Promise<string> {
  let lastError: Error | null = null;

  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const response = await fetch(url, {
        headers: CRICBUZZ_HEADERS,
        signal: AbortSignal.timeout(timeoutMs),
      });

      if (!response.ok) {
        // Only retry on 5xx (server) errors, not 4xx (client) errors
        if (response.status >= 500 && attempt < retries) {
          await sleep(500 * (attempt + 1));
          continue;
        }
        throw new Error(`Cricbuzz returned ${response.status}: ${response.statusText}`);
      }

      return await response.text();
    } catch (error) {
      lastError = error as Error;

      // Don't retry on abort (timeout) or 4xx — only network/5xx errors
      if (attempt < retries && !isAbortError(error)) {
        logger.warn(
          { attempt: attempt + 1, url, error: (error as Error).message },
          'Cricbuzz fetch retry',
        );
        await sleep(500 * (attempt + 1));
      }
    }
  }

  throw lastError ?? new Error(`Failed to fetch Cricbuzz page: ${url}`);
}

// ─── RSC chunk extraction ────────────────────────────────────────────

/**
 * Extract and concatenate all Next.js RSC streamed JSON chunks from HTML.
 *
 * The chunks are embedded as:
 *   self.__next_f.push([1,"...escaped JSON string..."])
 *
 * Returns null if no meaningful data was found.
 */
export function extractRSCChunks(html: string): string | null {
  const chunkRegex = /self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)/g;
  let allChunks = '';
  let match: RegExpExecArray | null;

  while ((match = chunkRegex.exec(html)) !== null) {
    try {
      // The chunk content is a JSON-escaped string — parse it to unescape
      allChunks += JSON.parse('"' + match[1] + '"');
    } catch {
      // Skip malformed chunks
    }
  }

  // A valid page will always have far more than 100 chars of RSC data
  return allChunks.length > 100 ? allChunks : null;
}

// ─── JSON extraction ─────────────────────────────────────────────────

/**
 * Extract a JSON object/array from a string by finding a key and then
 * using balanced-bracket parsing.
 *
 * More reliable than JSON.parse for partial RSC chunks where the overall
 * string isn't valid JSON but individual objects within it are.
 *
 * @param str - The RSC chunk string to search
 * @param key - The key to find, e.g. `"commentaryPageData":`
 */
export function extractJSONByKey(str: string, key: string): unknown | null {
  const startIdx = str.indexOf(key);
  if (startIdx === -1) return null;

  const objStart = startIdx + key.length;
  let depth = 0;
  let objEnd = -1;

  for (let i = objStart; i < str.length; i++) {
    const char = str[i];
    if (char === '{' || char === '[') depth++;
    else if (char === '}' || char === ']') {
      depth--;
      if (depth === 0) {
        objEnd = i;
        break;
      }
    }
  }

  if (objEnd !== -1) {
    try {
      return JSON.parse(str.slice(objStart, objEnd + 1));
    } catch {
      return null;
    }
  }

  return null;
}

/**
 * Extract the top-level `typeMatches` array from an RSC chunk string.
 *
 * Uses two strategies to handle different Cricbuzz page layouts:
 * - Strategy A: Find the distinctive `"matches":[{"matchType"` marker directly
 * - Strategy B: Anchor on the `pageType` marker and walk backwards
 *
 * This dual-strategy approach handles variations in how Cricbuzz structures
 * its RSC payloads across different page types (live vs upcoming vs recent).
 */
export function extractTypeMatches(allChunks: string, pageType?: string): unknown[] {
  const TOP_LEVEL_MARKER = '"matches":[{"matchType"';
  let typeMatches: unknown[] = [];

  // Strategy A: find the distinctive top-level pattern directly
  const markerIdx = allChunks.indexOf(TOP_LEVEL_MARKER);
  if (markerIdx !== -1) {
    const arrStart = markerIdx + '"matches":'.length;
    let depth = 0;
    let arrEnd = -1;

    for (let i = arrStart; i < allChunks.length; i++) {
      if (allChunks[i] === '[') depth++;
      else if (allChunks[i] === ']') {
        depth--;
        if (depth === 0) {
          arrEnd = i;
          break;
        }
      }
    }

    if (arrEnd !== -1) {
      try {
        typeMatches = JSON.parse(allChunks.slice(arrStart, arrEnd + 1)) as unknown[];
      } catch (error) {
        logger.warn({ error: (error as Error).message }, 'extractTypeMatches Strategy A parse error');
      }
    }
  }

  // Strategy B: anchor on the pageType marker and walk backwards
  if (typeMatches.length === 0 && pageType) {
    const pageTypeIdx = allChunks.indexOf(`"pageType":"${pageType}"`);
    if (pageTypeIdx !== -1) {
      const searchArea = allChunks.slice(0, pageTypeIdx);
      const specificMarkerIdx = searchArea.lastIndexOf(TOP_LEVEL_MARKER);

      if (specificMarkerIdx !== -1) {
        const arrStart = specificMarkerIdx + '"matches":'.length;
        let depth = 0;
        let arrEnd = -1;

        for (let i = arrStart; i < allChunks.length; i++) {
          if (allChunks[i] === '[') depth++;
          else if (allChunks[i] === ']') {
            depth--;
            if (depth === 0) {
              arrEnd = i;
              break;
            }
          }
        }

        if (arrEnd !== -1) {
          try {
            typeMatches = JSON.parse(allChunks.slice(arrStart, arrEnd + 1)) as unknown[];
          } catch (error) {
            logger.warn({ error: (error as Error).message }, 'extractTypeMatches Strategy B parse error');
          }
        }
      }
    }
  }

  return typeMatches;
}

/**
 * Extract `typeMatches` using the upcoming-page-specific nesting structure.
 *
 * The upcoming page uses a different RSC layout:
 *   currentMatchesList → typeMatches → [...]
 */
export function extractUpcomingTypeMatches(allChunks: string): unknown[] {
  for (const key of ['"currentMatchesList"', '"matchesList"']) {
    const keyIdx = allChunks.indexOf(key);
    if (keyIdx === -1) continue;

    const typeMatchesIdx = allChunks.indexOf('"typeMatches"', keyIdx);
    if (typeMatchesIdx === -1) continue;

    const arrStart = allChunks.indexOf('[', typeMatchesIdx);
    if (arrStart === -1) continue;

    let depth = 0;
    let arrEnd = -1;
    for (let i = arrStart; i < allChunks.length; i++) {
      if (allChunks[i] === '[') depth++;
      else if (allChunks[i] === ']') {
        depth--;
        if (depth === 0) {
          arrEnd = i;
          break;
        }
      }
    }

    if (arrEnd === -1) continue;

    try {
      const parsed = JSON.parse(allChunks.slice(arrStart, arrEnd + 1)) as unknown[];
      if (parsed.length > 0) return parsed;
    } catch {
      // Try next key
    }
  }

  return [];
}

// ─── String helpers ──────────────────────────────────────────────────

/**
 * Build a URL-safe slug from team names and match description.
 */
export function buildMatchSlug(
  team1Name: string,
  team2Name: string,
  matchDesc: string,
  seriesName: string,
): string {
  const t1 = toSlug(team1Name);
  const t2 = toSlug(team2Name);
  const desc = toSlug(matchDesc);
  const ser = toSlug(seriesName);
  return `${t1}-vs-${t2}-${desc}-${ser}`.replace(/-+/g, '-');
}

function toSlug(str: string): string {
  return str
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .trim();
}

/**
 * Build a Cricbuzz CDN flag URL from an imageId.
 */
export function buildFlagUrl(imageId: string | number | undefined, teamName: string): string | null {
  if (!imageId) return null;
  return `https://static.cricbuzz.com/a/img/v1/0x0/i1/c${imageId}/${toSlug(teamName)}.jpg`;
}

/**
 * Build a Cricbuzz player face image URL.
 */
export function buildPlayerImageUrl(faceImageId: string | number | undefined): string | null {
  if (!faceImageId) return null;
  return `https://static.cricbuzz.com/a/img/v1/0x0/i1/c${faceImageId}/player.jpg`;
}

// ─── Internal utilities ──────────────────────────────────────────────

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}
