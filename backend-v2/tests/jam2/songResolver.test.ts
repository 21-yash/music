import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  resolveSong,
  cacheResolvedSong,
  clearSongResolverCache,
  sweepExpiredSongCache,
  getSongResolverCacheSize,
  getSongResolverCacheEntry,
  MAX_SONG_CACHE_ENTRIES,
  NEGATIVE_CACHE_TTL_MS,
  CACHE_TTL_MS,
} from '../../src/jam2/songResolver';
import * as musicService from '../../src/services/musicService';
import { Song } from '../../src/jam2/types';

describe('Task R7: songResolver cache limits, LRU, negative-cache, and periodic sweep', () => {
  const makeSong = (id: string, providerId = 'jiosaavn'): Song => ({
    id,
    title: `Song ${id}`,
    artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
    album: null,
    duration: 180,
    imageUrl: null,
    year: '2024',
    language: 'en',
    hasLyrics: false,
    playCount: 0,
    label: '',
    streamRef: '',
    providerId,
  });

  beforeEach(() => {
    clearSongResolverCache();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    clearSongResolverCache();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('1. A burst of 600 distinct songs caps memory cache at 500, evicts oldest, and recently-read entries survive (LRU)', async () => {
    // Populate 0..499
    for (let i = 0; i < 500; i++) {
      cacheResolvedSong(makeSong(`song_${i}`));
    }
    expect(getSongResolverCacheSize()).toBe(500);

    // Read song_0 and song_1 via resolveSong so their LRU position moves to most recently used
    const res0 = await resolveSong('song_0', 'jiosaavn');
    const res1 = await resolveSong('song_1', 'jiosaavn');
    expect(res0?.id).toBe('song_0');
    expect(res1?.id).toBe('song_1');

    // Add 100 more songs (500..599), total burst across all was 600 distinct songs
    for (let i = 500; i < 600; i++) {
      cacheResolvedSong(makeSong(`song_${i}`));
    }

    // Cache size must remain capped at 500
    expect(getSongResolverCacheSize()).toBe(500);

    // song_2 was not touched and was oldest after song_0/1, so song_2 through song_101 were evicted
    expect(getSongResolverCacheEntry('jiosaavn:song_2')).toBeUndefined();
    expect(getSongResolverCacheEntry('jiosaavn:song_50')).toBeUndefined();

    // song_0 and song_1 must survive because they were read and bumped in LRU
    expect(getSongResolverCacheEntry('jiosaavn:song_0')).toBeDefined();
    expect(getSongResolverCacheEntry('jiosaavn:song_1')).toBeDefined();

    // The newly inserted entries (e.g. song_599) must be in cache
    expect(getSongResolverCacheEntry('jiosaavn:song_599')).toBeDefined();
  });

  it('2. A failed resolve is negative-cached for 30s; a second resolve within 30s does not call getSong; after 31s it does', async () => {
    vi.useFakeTimers();
    const getSongSpy = vi.spyOn(musicService, 'getSong');
    getSongSpy.mockRejectedValue(new Error('Provider lookup failed'));

    // First resolve fails
    const res1 = await resolveSong('fail_song_1', 'jiosaavn');
    expect(res1).toBeNull();
    expect(getSongSpy).toHaveBeenCalledTimes(1);

    // Second resolve at +10s uses negative cache without calling getSong
    vi.advanceTimersByTime(10_000);
    const res2 = await resolveSong('fail_song_1', 'jiosaavn');
    expect(res2).toBeNull();
    expect(getSongSpy).toHaveBeenCalledTimes(1);

    // At +29s: still within 30s negative cache
    vi.advanceTimersByTime(19_000);
    const res3 = await resolveSong('fail_song_1', 'jiosaavn');
    expect(res3).toBeNull();
    expect(getSongSpy).toHaveBeenCalledTimes(1);

    // At +31s total: negative cache has expired
    vi.advanceTimersByTime(2_000);
    getSongSpy.mockResolvedValueOnce({
      id: 'fail_song_1',
      title: 'Now Available Song',
      artists: [{ id: 'a1', name: 'Artist' }],
      duration: 200,
      providerId: 'jiosaavn',
    } as any);

    const res4 = await resolveSong('fail_song_1', 'jiosaavn');
    expect(res4).not.toBeNull();
    expect(res4?.title).toBe('Now Available Song');
    expect(getSongSpy).toHaveBeenCalledTimes(2);
  });

  it('3. sweepExpiredSongCache removes expired positive and negative entries', async () => {
    // Cache a positive song with standard TTL
    cacheResolvedSong(makeSong('pos_1'));

    // Manually trigger a negative cache entry by failing a lookup
    const getSongSpy = vi.spyOn(musicService, 'getSong');
    getSongSpy.mockRejectedValue(new Error('Lookup error'));
    await resolveSong('neg_expired', 'jiosaavn');

    // Both entries exist
    expect(getSongResolverCacheEntry('jiosaavn:pos_1')).toBeDefined();
    expect(getSongResolverCacheEntry('jiosaavn:neg_expired')).toBeDefined();

    // Fast-forward now past negative cache TTL (30s)
    const sweepTime = Date.now() + 35_000;

    // Positive entry TTL is 10 minutes, so pos_1 has not expired yet at +35s
    // But negative entry (TTL 30s) HAS expired at +35s
    let evicted = sweepExpiredSongCache(sweepTime);
    expect(evicted).toBe(1);
    expect(getSongResolverCacheEntry('jiosaavn:neg_expired')).toBeUndefined();
    expect(getSongResolverCacheEntry('jiosaavn:pos_1')).toBeDefined();

    // Now advance past 10 minutes (CACHE_TTL_MS = 600,000ms)
    const farFuture = Date.now() + CACHE_TTL_MS + 1000;
    evicted = sweepExpiredSongCache(farFuture);
    expect(evicted).toBeGreaterThanOrEqual(1);
    expect(getSongResolverCacheEntry('jiosaavn:pos_1')).toBeUndefined();
    expect(getSongResolverCacheSize()).toBe(0);
  });
});
