import type { HighlightSource, MatchHighlight } from '../types';
import { getRedisClient } from '../../../config/redis';
import { logger } from '../../../utils/logger';
import {
  parseDurationToSeconds,
  scoreHighlightMatch,
  type MatchCandidateContext,
} from './highlightMatcher';

interface BffVideoItem {
  id?: string;
  title?: string;
  slug?: string;
  thumbnailUrl?: string;
  publishedDate?: string;
  duration?: string;
  playbackUrl?: string;
}

interface BffVideosResponse {
  data?: {
    videos?: BffVideoItem[];
    videosMeta?: {
      totalPages?: number;
    };
  };
}

export interface MuxBffProviderConfig {
  source: Exclude<HighlightSource, 'youtube'>;
  publisher: string;
  baseUrl: string;
  maxPagesToScan?: number;
  playbackHeaders?: Record<string, string>;
}

const CATALOG_CACHE_TTL_SECONDS = 600; // 10 minutes — safely below Mux JWT expiry
const REQUEST_TIMEOUT_MS = 10_000;

/**
 * Shared provider for BCCI.tv and IPLT20.com Next.js BFF highlight endpoints.
 *
 * Both sites expose identical JSON structures at:
 *   GET {baseUrl}/api/bff/cms/videos?category=highlights&page={n}
 * returning signed Mux HLS (.m3u8) streams and Staylive thumbnails.
 */
export class MuxBffHighlightsProvider {
  private readonly maxPages: number;

  constructor(private readonly config: MuxBffProviderConfig) {
    this.maxPages = config.maxPagesToScan ?? 5;
  }

  async findHighlights(match: MatchCandidateContext): Promise<MatchHighlight[]> {
    const scored: Array<{ highlight: MatchHighlight; score: number }> = [];
    const seenIds = new Set<string>();
    const stopCutoffMs = match.startTimestamp
      ? match.startTimestamp - 10 * 24 * 60 * 60 * 1000
      : null;

    for (let page = 1; page <= this.maxPages; page++) {
      const videos = await this.fetchCatalogPage(page);
      if (videos.length === 0) break;

      let oldestOnPageMs: number | null = null;

      for (const item of videos) {
        if (!item.id || !item.title || !item.playbackUrl || seenIds.has(item.id)) {
          continue;
        }

        if (item.publishedDate) {
          const pubMs = Date.parse(item.publishedDate);
          if (!Number.isNaN(pubMs)) {
            if (oldestOnPageMs === null || pubMs < oldestOnPageMs) {
              oldestOnPageMs = pubMs;
            }
          }
        }

        const durationSeconds = parseDurationToSeconds(item.duration);
        const score = scoreHighlightMatch(match, {
          title: item.title,
          publishedAt: item.publishedDate ?? null,
          durationSeconds,
        });

        if (score === null) continue;

        seenIds.add(item.id);
        const slugPath = item.slug ? `/videos/${item.slug}` : '/videos';
        scored.push({
          score,
          highlight: {
            id: `${this.config.source}:${item.id}`,
            title: item.title,
            thumbnailUrl: item.thumbnailUrl ?? null,
            duration: item.duration ?? '0:00',
            durationSeconds,
            publishedAt: item.publishedDate ?? null,
            source: this.config.source,
            playbackType: 'hls',
            streamTarget: item.playbackUrl,
            externalUrl: `${this.config.baseUrl}${slugPath}`,
            publisher: this.config.publisher,
            ...(this.config.playbackHeaders ? { playbackHeaders: this.config.playbackHeaders } : {}),
          },
        });
      }

      // Catalog is newest-first; once we pass 10 days before match start, stop scanning
      if (stopCutoffMs !== null && oldestOnPageMs !== null && oldestOnPageMs < stopCutoffMs) {
        break;
      }
    }

    scored.sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      const timeA = a.highlight.publishedAt ? Date.parse(a.highlight.publishedAt) : 0;
      const timeB = b.highlight.publishedAt ? Date.parse(b.highlight.publishedAt) : 0;
      return timeA - timeB;
    });

    return scored.map((entry) => entry.highlight);
  }

  private async fetchCatalogPage(page: number): Promise<BffVideoItem[]> {
    const cacheKey = `sports:highlights:catalog:${this.config.source}:page:${page}`;
    try {
      const redis = getRedisClient();
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached) as BffVideoItem[];
      }
    } catch {
      // Non-fatal: fall through to origin fetch
    }

    const url = `${this.config.baseUrl}/api/bff/cms/videos?category=highlights&page=${page}`;
    try {
      const response = await fetch(url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          Accept: 'application/json',
          Referer: `${this.config.baseUrl}/videos`,
        },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });

      if (!response.ok) {
        logger.warn({ source: this.config.source, page, status: response.status }, 'Highlights BFF fetch failed');
        return [];
      }

      const body = (await response.json()) as BffVideosResponse;
      const videos = Array.isArray(body.data?.videos) ? body.data.videos : [];

      if (videos.length > 0) {
        try {
          const redis = getRedisClient();
          await redis.setex(cacheKey, CATALOG_CACHE_TTL_SECONDS, JSON.stringify(videos));
        } catch {
          // Non-fatal cache write error
        }
      }

      return videos;
    } catch (error) {
      logger.warn({ source: this.config.source, page, error: (error as Error).message }, 'Highlights BFF request error');
      return [];
    }
  }
}

export const bcciHighlightsProvider = new MuxBffHighlightsProvider({
  source: 'bcci',
  publisher: 'BCCI',
  baseUrl: 'https://www.bcci.tv',
  maxPagesToScan: 5,
});

export const iplHighlightsProvider = new MuxBffHighlightsProvider({
  source: 'iplt20',
  publisher: 'IPL',
  baseUrl: 'https://www.iplt20.com',
  maxPagesToScan: 6,
  playbackHeaders: {
    Referer: 'https://www.iplt20.com/',
    Origin: 'https://www.iplt20.com',
  },
});
