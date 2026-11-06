import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as musicService from '../../src/services/musicService';
import * as songResolver from '../../src/jam2/songResolver';
import * as redisConfig from '../../src/config/redis';
import { AppError } from '../../src/utils/errors';

describe('Task H5 backend stream token and retry hardening', () => {
  const mockRedis = {
    get: vi.fn().mockResolvedValue(null),
    setex: vi.fn().mockResolvedValue('OK'),
    pipeline: vi.fn().mockReturnValue({
      setex: vi.fn().mockReturnThis(),
      exec: vi.fn().mockResolvedValue([]),
    }),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    songResolver.clearSongResolverCache();
    vi.spyOn(redisConfig, 'getRedisClient').mockReturnValue(mockRedis as any);
  });

  it('songResolver success stores the token (spy on the helper) and the client song still has streamRef empty', async () => {
    const mockYouTubeProvider = {
      id: 'youtube',
      name: 'youtube',
      getSongIsReliable: false,
      getSong: vi.fn().mockResolvedValue(null),
    };
    musicService.registerMusicProvider(mockYouTubeProvider as any, false);

    const storeSpy = vi.spyOn(musicService, 'storeStreamTokensFor');

    // YouTube song with client metadata fallback
    const clientSong = {
      id: 'dQw4w9WgXcQ',
      title: 'Never Gonna Give You Up',
      artists: [{ id: 'a1', name: 'Rick Astley' }],
      duration: 212,
      providerId: 'youtube',
    } as any;

    const resolved = await songResolver.resolveSongOrFallback(clientSong);

    expect(resolved).not.toBeNull();
    expect(resolved!.streamRef).toBe(''); // Never leaks streamRef to client
    expect(storeSpy).toHaveBeenCalledWith(expect.objectContaining({
      id: 'dQw4w9WgXcQ',
      streamRef: 'dQw4w9WgXcQ',
    }));
  });

  it('resolveStreamUrl retries once then succeeds', async () => {
    const mockProvider = {
      id: 'test_provider_retry',
      name: 'test_provider_retry',
      getSong: vi.fn()
        .mockRejectedValueOnce(new Error('Transient network error'))
        .mockResolvedValueOnce({
          id: 'test_song_1',
          title: 'Test Song',
          streamRef: 'encrypted_stream_ref_123',
          providerId: 'test_provider_retry',
        }),
      resolveStreamUrl: vi.fn().mockResolvedValue({
        type: 'direct',
        url: 'https://cdn.example.com/audio.mp4',
        quality: 'high',
      }),
    };
    musicService.registerMusicProvider(mockProvider as any, false);

    const stream = await musicService.resolveStreamUrl('test_song_1', 'high', 'test_provider_retry');
    expect(mockProvider.getSong).toHaveBeenCalledTimes(2);
    expect(stream.url).toBe('https://cdn.example.com/audio.mp4');
  });

  it('resolveStreamUrl after two failures throws the clean error', async () => {
    const mockProvider = {
      id: 'test_provider_fail',
      name: 'test_provider_fail',
      getSong: vi.fn()
        .mockRejectedValueOnce(new Error('Persistent provider failure'))
        .mockRejectedValueOnce(new Error('Persistent provider failure')),
      resolveStreamUrl: vi.fn(),
    };
    musicService.registerMusicProvider(mockProvider as any, false);

    await expect(
      musicService.resolveStreamUrl('test_song_2', 'high', 'test_provider_fail')
    ).rejects.toSatisfy((err: any) => {
      expect(err).toBeInstanceOf(AppError);
      expect(err.statusCode).toBe(404);
      expect(err.code).toBe('STREAM_UNAVAILABLE');
      expect(err.message).toBe('Stream unavailable for this song');
      return true;
    });
    expect(mockProvider.getSong).toHaveBeenCalledTimes(2);
  });
});
