import type { MatchHighlight } from '../types';
import { logger } from '../../../utils/logger';
import {
  parseDurationToSeconds,
  scoreHighlightMatch,
  type MatchCandidateContext,
} from './highlightMatcher';

/**
 * Curated allowlist of official cricket board, ICC, and major league YouTube channels.
 * Prevents fan edits, clickbait uploads, and cricket video-game streams from appearing.
 */
export const OFFICIAL_CRICKET_CHANNELS: ReadonlyMap<string, string> = new Map([
  ['UCkBY0aHJP9BwjZLDYxAQrKg', 'cricket.com.au'],
  ['UCz1D0n02LR3t-47fM4x7Auw', 'England & Wales Cricket Board'],
  ['UCi-5OZ2tYjsW2-8d-U_7Xog', 'England & Wales Cricket Board'],
  ['UCt2JXN_xL_G0nJ-o13K65Wg', 'ICC'],
  ['UC-560Q9082t83zU0h_k1gCg', 'ICC'],
  ['UC7ITT3ooYWDYY_ehIUbt6eg', 'Asian Cricket Council'],
  ['UC3s6K2kZ2w73a7_h08c4-hA', 'Pakistan Cricket'],
  ['UCO6gN-vI9k02L1Q2E5x8k2g', 'Sri Lanka Cricket'],
  ['UCr9aK3E9n7jW1q9F_6k_t8Q', 'Sri Lanka Cricket'],
  ['UC2t5bjwFrE2h_Q-2l9R6E2w', 'Windies Cricket'],
  ['UC0y6P-6W_R7m5J6z5c-q7Cg', 'BLACKCAPS'],
  ['UC8p4g7V_0y_sK2qG5S2tJ2Q', 'Cricket South Africa'],
  ['UCa-0l9X-V5T6n1Q8K-4qG3A', 'Bangladesh Cricket'],
  ['UCF7a7e0-i0u-Q4e4o5f8h1Q', 'Zimbabwe Cricket'],
  ['UCW5C8xS5V5Q4m4y8n7o-4XQ', 'Cricket Ireland'],
  ['UCd1M7xG7tH5uR6S6c3i8w2w', 'Prime Video Sport'],
  ['UCx2Z-1V6nQ5k8M7l0N7c8bA', 'SonyLIV'],
  ['UCu2X7-3Q1v0m7l8p9q3r6sQ', 'FanCode'],
  ['UCk4f1W6N2J7q4m3x8l9v5Zw', 'The Hundred'],
  ['UCh6g9C3S8V5Q0w3b2q9m1aA', 'Big Bash League'],
  ['UCJ0m4W8w5N0p6v2f4q7e3rA', 'SA20'],
  ['UC9r8Q4m3n2b1v0c7x6z5a4S', 'CPL T20'],
]);

const OFFICIAL_CHANNEL_NAMES = new Set([
  'cricket.com.au',
  'england & wales cricket board',
  'icc',
  'asian cricket council',
  'pakistan cricket',
  'sri lanka cricket',
  'windies cricket',
  'blackcaps',
  'nz cricket',
  'cricket south africa',
  'bangladesh cricket : the tigers',
  'bangladesh cricket',
  'zimbabwe cricket',
  'cricket ireland',
  'the hundred',
  'big bash league',
  'sa20',
  'cpl t20',
  'major league cricket',
  'pakistan super league',
  'sony liv',
  'sonyliv',
  'fancode',
  'prime video sport',
]);

const MIN_HIGHLIGHT_DURATION_SECONDS = 90;
const YOUTUBE_SEARCH_TIMEOUT_MS = 8_000;

interface ParsedYouTubeVideo {
  videoId: string;
  title: string;
  channelName: string;
  channelId: string;
  duration: string;
  durationSeconds: number;
  publishedText: string | null;
  thumbnailUrl: string | null;
}

/**
 * Discovers official match highlight videos on YouTube.
 *
 * Returns YouTube videoIds (`playbackType: 'youtube'`) rather than direct media URLs;
 * the Android client resolves streams on-device via NewPipeExtractor from the user's IP.
 */
export class YouTubeHighlightsProvider {
  async findHighlights(match: MatchCandidateContext): Promise<MatchHighlight[]> {
    const query = this.buildSearchQuery(match);
    const candidates = await this.searchYouTube(query);
    if (candidates.length === 0) return [];

    const scored: Array<{ highlight: MatchHighlight; score: number }> = [];
    const seen = new Set<string>();

    for (const candidate of candidates) {
      if (seen.has(candidate.videoId)) continue;
      if (!this.isOfficialChannel(candidate.channelId, candidate.channelName)) continue;
      if (candidate.durationSeconds > 0 && candidate.durationSeconds < MIN_HIGHLIGHT_DURATION_SECONDS) {
        continue;
      }

      // YouTube search returns relative publish strings ("1d ago"), so skip ISO date filtering
      const score = scoreHighlightMatch(match, {
        title: candidate.title,
        publishedAt: null,
        durationSeconds: candidate.durationSeconds,
      });

      if (score === null) continue;

      seen.add(candidate.videoId);
      const publisher =
        OFFICIAL_CRICKET_CHANNELS.get(candidate.channelId) ?? candidate.channelName;

      scored.push({
        score,
        highlight: {
          id: `youtube:${candidate.videoId}`,
          title: candidate.title,
          thumbnailUrl: candidate.thumbnailUrl,
          duration: candidate.duration || '0:00',
          durationSeconds: candidate.durationSeconds,
          publishedAt: candidate.publishedText,
          source: 'youtube',
          playbackType: 'youtube',
          streamTarget: candidate.videoId,
          externalUrl: `https://www.youtube.com/watch?v=${candidate.videoId}`,
          publisher,
        },
      });
    }

    scored.sort((a, b) => b.score - a.score);
    return scored.slice(0, 6).map((entry) => entry.highlight);
  }

  private buildSearchQuery(match: MatchCandidateContext): string {
    const year = match.startTimestamp
      ? String(new Date(match.startTimestamp).getUTCFullYear())
      : '';
    const desc = match.matchDesc ? match.matchDesc : '';
    return `${match.team1Name} vs ${match.team2Name} ${desc} ${year} highlights`.replace(/\s+/g, ' ').trim();
  }

  private isOfficialChannel(channelId: string, channelName: string): boolean {
    if (channelId && OFFICIAL_CRICKET_CHANNELS.has(channelId)) {
      return true;
    }
    return OFFICIAL_CHANNEL_NAMES.has(channelName.trim().toLowerCase());
  }

  private async searchYouTube(query: string): Promise<ParsedYouTubeVideo[]> {
    const url = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`;
    try {
      const response = await fetch(url, {
        headers: {
          'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
          'Accept-Language': 'en-US,en;q=0.9',
        },
        signal: AbortSignal.timeout(YOUTUBE_SEARCH_TIMEOUT_MS),
      });

      if (!response.ok) {
        logger.warn({ status: response.status, query }, 'YouTube highlights search failed');
        return [];
      }

      const html = await response.text();
      return parseYouTubeInitialData(html);
    } catch (error) {
      logger.warn({ error: (error as Error).message, query }, 'YouTube highlights search error');
      return [];
    }
  }
}

/**
 * Parse videoRenderer items from YouTube's embedded `ytInitialData` JSON script.
 * Exported for unit testing without live network calls.
 */
export function parseYouTubeInitialData(html: string): ParsedYouTubeVideo[] {
  const match = html.match(/var ytInitialData = (\{.*?\});<\/script>/);
  if (!match) return [];

  try {
    const data = JSON.parse(match[1]) as Record<string, any>;
    const sectionContents =
      data?.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer
        ?.contents ?? [];

    const results: ParsedYouTubeVideo[] = [];

    for (const section of sectionContents) {
      const items = section?.itemSectionRenderer?.contents ?? [];
      for (const item of items) {
        const renderer = item?.videoRenderer;
        if (!renderer?.videoId) continue;

        const title = renderer.title?.runs?.[0]?.text ?? '';
        const channelName = renderer.ownerText?.runs?.[0]?.text ?? '';
        const channelId =
          renderer.ownerText?.runs?.[0]?.navigationEndpoint?.browseEndpoint?.browseId ?? '';
        const duration = renderer.lengthText?.simpleText ?? '';
        const durationSeconds = parseDurationToSeconds(duration);
        const publishedText = renderer.publishedTimeText?.simpleText ?? null;
        const thumbnails = renderer.thumbnail?.thumbnails as Array<{ url?: string }> | undefined;
        const thumbnailUrl = thumbnails?.[thumbnails.length - 1]?.url ?? null;

        results.push({
          videoId: String(renderer.videoId),
          title: String(title),
          channelName: String(channelName),
          channelId: String(channelId),
          duration: String(duration),
          durationSeconds,
          publishedText: publishedText ? String(publishedText) : null,
          thumbnailUrl,
        });
      }
    }

    return results;
  } catch {
    return [];
  }
}

export const youtubeHighlightsProvider = new YouTubeHighlightsProvider();
