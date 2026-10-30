import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  mergeResolvedSong,
  resolveSongOrFallback,
  sanitizeUrl,
  clearSongResolverCache,
  setCustomSongResolver,
} from '../../src/jam2/songResolver';
import { Song, Room } from '../../src/jam2/types';
import { registerMusicProvider } from '../../src/services/musicService';
import { MusicProvider } from '../../src/providers/music/MusicProvider';
import { applyCommand } from '../../src/jam2/reducer';
import { Jam2Manager } from '../../src/jam2/manager';
import { Jam2Store } from '../../src/jam2/store';

describe('Task F3: mergeResolvedSong', () => {
  const baseClientSong: Song = {
    id: 'dQw4w9WgXcQ',
    title: 'Client Title',
    artists: [{ id: 'a1', name: 'Client Artist', imageUrl: null }],
    album: { id: 'alb1', title: 'Client Album', imageUrl: null },
    duration: 213,
    imageUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg',
    year: '1987',
    language: 'en',
    hasLyrics: true,
    playCount: 100,
    label: 'RCA',
    streamRef: 'malicious-client-stream-ref',
    providerId: 'youtube',
  };

  it('keeps client duration when resolved duration is 0', () => {
    const resolved: Song = {
      id: 'dQw4w9WgXcQ',
      title: 'Resolved Title',
      artists: [{ id: 'ra1', name: 'Resolved Artist', imageUrl: null }],
      album: null,
      duration: 0,
      imageUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg',
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: 'resolved-stream',
      providerId: 'youtube',
    };

    const merged = mergeResolvedSong(baseClientSong, resolved);
    expect(merged.duration).toBe(213);
    expect(merged.title).toBe('Resolved Title');
    expect(merged.streamRef).toBe('resolved-stream');
  });

  it('takes resolved duration when resolved duration > 0', () => {
    const resolved: Song = {
      ...baseClientSong,
      duration: 220,
      streamRef: 'resolved-stream',
    };

    const merged = mergeResolvedSong(baseClientSong, resolved);
    expect(merged.duration).toBe(220);
  });

  it('zeroes duration if client duration is negative or exceeds 6 hours', () => {
    const resolved: Song = {
      ...baseClientSong,
      duration: 0,
      streamRef: '',
    };

    const mergedNegative = mergeResolvedSong({ ...baseClientSong, duration: -10 }, resolved);
    expect(mergedNegative.duration).toBe(0);

    const mergedExcessive = mergeResolvedSong({ ...baseClientSong, duration: 25000 }, resolved);
    expect(mergedExcessive.duration).toBe(0);
  });

  it('never trusts client streamRef', () => {
    const resolved: Song = {
      ...baseClientSong,
      streamRef: '',
    };
    const merged = mergeResolvedSong(baseClientSong, resolved);
    expect(merged.streamRef).toBe('');
  });

  it('F3 table: resolver 240 + client 5 -> 240', () => {
    const resolved: Song = {
      ...baseClientSong,
      duration: 240,
    };
    const merged = mergeResolvedSong({ ...baseClientSong, duration: 5 }, resolved);
    expect(merged.duration).toBe(240);
  });

  it('F3 table: resolver 0 + client 99999 -> 0', () => {
    const resolved: Song = {
      ...baseClientSong,
      duration: 0,
    };
    const merged = mergeResolvedSong({ ...baseClientSong, duration: 99999 }, resolved);
    expect(merged.duration).toBe(0);
  });
});

describe('Task F14 & F11.e: Song resolution & sanitization', () => {
  beforeEach(() => {
    clearSongResolverCache();
    setCustomSongResolver(null);
  });

  afterEach(() => {
    delete process.env.JAM_ALLOWED_IMAGE_HOSTS;
  });

  it('F11.e & G9.f: sanitizeUrl allows valid image hosts and rejects removed/unknown domains', () => {
    // Active allowed hosts
    expect(sanitizeUrl('https://i.ytimg.com/vi/123/default.jpg')).toBe('https://i.ytimg.com/vi/123/default.jpg');
    expect(sanitizeUrl('https://c.saavncdn.com/123/song.jpg')).toBe('https://c.saavncdn.com/123/song.jpg');
    expect(sanitizeUrl('https://i.scdn.co/image/ab67616d0000b273')).toBe('https://i.scdn.co/image/ab67616d0000b273');
    expect(sanitizeUrl('https://lh3.googleusercontent.com/a/default-user')).toBe('https://lh3.googleusercontent.com/a/default-user');
    expect(sanitizeUrl('https://yt3.ggpht.com/ytc/artist-avatar')).toBe('https://yt3.ggpht.com/ytc/artist-avatar');

    // Removed / rejected domains (G9.f: cricbuzz and spotifycdn removed)
    expect(sanitizeUrl('https://images.cricbuzz.com/leaderboard.jpg')).toBeNull();
    expect(sanitizeUrl('https://lineup-images.spotifycdn.com/artist.jpg')).toBeNull();
    expect(sanitizeUrl('https://malicious.evil.com/hack.jpg')).toBeNull();
    expect(sanitizeUrl('http://i.ytimg.com/vi/123/default.jpg')).toBeNull(); // not https

    process.env.JAM_ALLOWED_IMAGE_HOSTS = 'customcdn.org, trusted.net';
    expect(sanitizeUrl('https://img.customcdn.org/pic.png')).toBe('https://img.customcdn.org/pic.png');
    expect(sanitizeUrl('https://trusted.net/pic.png')).toBe('https://trusted.net/pic.png');
  });

  it('F14: YouTube song accepted with client metadata fallback when getSong returns null', async () => {
    const mockYouTubeProvider: MusicProvider = {
      id: 'youtube_test',
      name: 'YouTube Test',
      getSongIsReliable: false,
      search: vi.fn(),
      getTrending: vi.fn(),
      getHomeData: vi.fn(),
      getSong: vi.fn().mockResolvedValue(null),
      getAlbum: vi.fn(),
      getArtist: vi.fn(),
      getPlaylist: vi.fn(),
      getSongsByIds: vi.fn(),
      getRelatedSongs: vi.fn(),
    };
    registerMusicProvider(mockYouTubeProvider, false);

    const clientSong: Song = {
      id: 'dQw4w9WgXcQ',
      title: 'Rick Astley - Never Gonna Give You Up',
      artists: [{ id: 'a1', name: 'Rick Astley', imageUrl: null }],
      album: null,
      duration: 213,
      imageUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg',
      year: '1987',
      language: 'en',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: 'client-stream-ref',
      providerId: 'youtube_test',
    };

    const resolved = await resolveSongOrFallback(clientSong);
    expect(resolved).not.toBeNull();
    expect(resolved?.id).toBe('dQw4w9WgXcQ');
    expect(resolved?.title).toBe('Rick Astley - Never Gonna Give You Up');
    expect(resolved?.streamRef).toBe(''); // discarded
  });

  it('F14: JioSaavn song with getSong returning null is STRICTLY rejected', async () => {
    const mockJioSaavnProvider: MusicProvider = {
      id: 'jiosaavn_test',
      name: 'JioSaavn Test',
      getSongIsReliable: true,
      search: vi.fn(),
      getTrending: vi.fn(),
      getHomeData: vi.fn(),
      getSong: vi.fn().mockResolvedValue(null),
      getAlbum: vi.fn(),
      getArtist: vi.fn(),
      getPlaylist: vi.fn(),
      getSongsByIds: vi.fn(),
      getRelatedSongs: vi.fn(),
    };
    registerMusicProvider(mockJioSaavnProvider, false);

    const clientSong: Song = {
      id: 'js_123',
      title: 'JioSaavn Song',
      artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
      album: null,
      duration: 200,
      imageUrl: 'https://c.saavncdn.com/123/song.jpg',
      year: '2020',
      language: 'hi',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: 'client-stream-ref',
      providerId: 'jiosaavn_test',
    };

    const resolved = await resolveSongOrFallback(clientSong);
    expect(resolved).toBeNull();
  });

  it('G9.d: Unreliable provider whose getSong throws is accepted via fallback metadata', async () => {
    const mockUnreliableProvider: MusicProvider = {
      id: 'unreliable_throwing',
      name: 'Unreliable Throwing Provider',
      getSongIsReliable: false,
      search: vi.fn(),
      getTrending: vi.fn(),
      getHomeData: vi.fn(),
      getSong: vi.fn().mockRejectedValue(new Error('Network failure / scraping blocked')),
      getAlbum: vi.fn(),
      getArtist: vi.fn(),
      getPlaylist: vi.fn(),
      getSongsByIds: vi.fn(),
      getRelatedSongs: vi.fn(),
    };
    registerMusicProvider(mockUnreliableProvider, false);

    const clientSong: Song = {
      id: 'fallback_ok_1',
      title: 'Fallback Song',
      artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
      album: null,
      duration: 180,
      imageUrl: 'https://c.saavncdn.com/123/song.jpg',
      year: '2024',
      language: 'en',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: 'client-stream',
      providerId: 'unreliable_throwing',
    };

    const resolved = await resolveSongOrFallback(clientSong);
    expect(resolved).not.toBeNull();
    expect(resolved?.id).toBe('fallback_ok_1');
    expect(resolved?.title).toBe('Fallback Song');
    expect(resolved?.streamRef).toBe(''); // discarded
  });

  it('G9.d: Reliable provider throwing returns null (strictly rejected)', async () => {
    const mockReliableProvider: MusicProvider = {
      id: 'reliable_throwing',
      name: 'Reliable Throwing Provider',
      getSongIsReliable: true,
      search: vi.fn(),
      getTrending: vi.fn(),
      getHomeData: vi.fn(),
      getSong: vi.fn().mockRejectedValue(new Error('Database error')),
      getAlbum: vi.fn(),
      getArtist: vi.fn(),
      getPlaylist: vi.fn(),
      getSongsByIds: vi.fn(),
      getRelatedSongs: vi.fn(),
    };
    registerMusicProvider(mockReliableProvider, false);

    const clientSong: Song = {
      id: 'rel_fail_1',
      title: 'Reliable Failed Song',
      artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
      album: null,
      duration: 180,
      imageUrl: null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: 'client-stream',
      providerId: 'reliable_throwing',
    };

    const resolved = await resolveSongOrFallback(clientSong);
    expect(resolved).toBeNull();
  });

  it('G9.d: Unregistered providerId returns null', async () => {
    const clientSong: Song = {
      id: 'unregistered_123',
      title: 'Unknown Provider Song',
      artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
      album: null,
      duration: 180,
      imageUrl: null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: '',
      providerId: 'completely_unknown_provider_xyz',
    };

    const resolved = await resolveSongOrFallback(clientSong);
    expect(resolved).toBeNull();
  });

  it('G9.d: YouTube id with invalid/bad shape returns null', async () => {
    const mockYouTubeProvider: MusicProvider = {
      id: 'youtube',
      name: 'YouTube',
      getSongIsReliable: false,
      search: vi.fn(),
      getTrending: vi.fn(),
      getHomeData: vi.fn(),
      getSong: vi.fn().mockResolvedValue(null),
      getAlbum: vi.fn(),
      getArtist: vi.fn(),
      getPlaylist: vi.fn(),
      getSongsByIds: vi.fn(),
      getRelatedSongs: vi.fn(),
    };
    registerMusicProvider(mockYouTubeProvider, false);

    const badIdSong: Song = {
      id: 'bad_id!@#', // not 11 chars [A-Za-z0-9_-]
      title: 'Bad ID Song',
      artists: [{ id: 'a1', name: 'Artist', imageUrl: null }],
      album: null,
      duration: 200,
      imageUrl: null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: '',
      providerId: 'youtube',
    };

    const resolved = await resolveSongOrFallback(badIdSong);
    expect(resolved).toBeNull();
  });
});

describe('Task F6: Jam queue duplicate detection', () => {
  const song1: Song = {
    id: 's1',
    title: 'Song One',
    artists: [{ id: 'a1', name: 'Artist A', imageUrl: null }],
    album: null,
    duration: 180,
    imageUrl: null,
    year: '',
    language: '',
    hasLyrics: false,
    playCount: 0,
    label: '',
    streamRef: '',
    providerId: 'youtube',
  };

  const song1Acoustic: Song = {
    id: 's1_acoustic',
    title: 'Song One',
    artists: [{ id: 'a1', name: 'Artist A', imageUrl: null }],
    album: null,
    duration: 195,
    imageUrl: null,
    year: '',
    language: '',
    hasLyrics: false,
    playCount: 0,
    label: '',
    streamRef: '',
    providerId: 'youtube',
  };

  const createTestRoom = (): Room => ({
    code: 'TEST',
    version: 1,
    hostUserId: 'u1',
    createdAt: Date.now(),
    members: new Map([
      ['u1', { userId: 'u1', username: 'Host', joinedAt: Date.now(), online: true, lastSeenAt: Date.now(), perms: { queueAdd: true, playback: true, queueManage: true } }],
    ]),
    defaults: { queueAdd: true, playback: false, queueManage: false },
    queue: [],
    history: [],
    nowPlaying: {
      item: { qid: 'q1', song: song1, addedByUserId: 'u1', addedAt: Date.now(), recommended: false },
      status: 'playing',
      positionMs: 0,
      anchorServerTs: Date.now(),
      trackSeq: 1,
    },
  });

  it('rejects duplicate song with same providerId and id currently playing', () => {
    const room = createTestRoom();
    const res = applyCommand(room, { type: 'queueAdd', song: song1 }, 'u1', Date.now());
    expect('room' in res && (res as any).skippedDuplicate).toBe(1);
  });

  it('allows different version (different id) of same title/artist to be queued by user', () => {
    const room = createTestRoom();
    const res = applyCommand(room, { type: 'queueAdd', song: song1Acoustic }, 'u1', Date.now());
    expect('room' in res && (res as any).added).toBe(1);
    if ('room' in res) {
      expect(res.room.queue.some((q) => q.song.id === 's1_acoustic')).toBe(true);
    }
  });

  it('allows re-adding a song that has already finished and is in history', () => {
    const room = createTestRoom();
    // Simulate song1 moving to history and nowPlaying is a different song
    room.history.push({ qid: 'q1', song: song1, addedByUserId: 'u1', addedAt: Date.now() - 50000, recommended: false });
    room.nowPlaying.item = { qid: 'q2', song: song1Acoustic, addedByUserId: 'u1', addedAt: Date.now(), recommended: false };

    const res = applyCommand(room, { type: 'queueAdd', song: song1 }, 'u1', Date.now());
    expect('room' in res && (res as any).added).toBe(1);
    if ('room' in res) {
      expect(res.room.queue.some((q) => q.song.id === 's1')).toBe(true);
    }
  });
});

describe('Task F11.d: Recommendation 30s failure backoff', () => {
  it('does not re-trigger recommendations within 30s of a failure', async () => {
    let fetchCount = 0;
    const failingFetcher = vi.fn().mockImplementation(async () => {
      fetchCount++;
      throw new Error('Network error');
    });

    const store = new Jam2Store(false);
    const manager = new Jam2Manager(store, failingFetcher);

    const song: Song = {
      id: 's_rec',
      title: 'Rec Seed',
      artists: [{ id: 'a1', name: 'Rec Artist', imageUrl: null }],
      album: null,
      duration: 180,
      imageUrl: null,
      year: '',
      language: '',
      hasLyrics: false,
      playCount: 0,
      label: '',
      streamRef: '',
      providerId: 'youtube',
    };

    const room = await manager.createRoom('u1', 'Host');
    // Set room to playing with idle user queue
    await manager.executeCommand(room.code, { type: 'queueAdd', song }, 'u1');

    // First attempt will fail and record recsFailedAt
    await new Promise((r) => setTimeout(r, 100));
    expect(fetchCount).toBe(1);

    // Call triggerRecommendationsIfNeeded again immediately
    const freshRoom = await store.getRoom(room.code);
    manager.triggerRecommendationsIfNeeded(freshRoom!);
    await new Promise((r) => setTimeout(r, 100));

    // fetchCount should STILL be 1 because of 30s failure backoff
    expect(fetchCount).toBe(1);
  });
});
