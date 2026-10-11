import { describe, it, expect } from 'vitest';
import {
  likeSongSchema,
  checkLikesQuerySchema,
  syncLikesSchema,
  createPlaylistSchema,
  updatePlaylistSchema,
  addPlaylistSongSchema,
  reorderPlaylistSongsSchema,
  followTeamSchema,
  checkTeamsQuerySchema,
} from '../../src/routes/schemas/librarySchemas';

describe('likeSongSchema', () => {
  it('accepts valid song data', () => {
    const result = likeSongSchema.safeParse({
      songId: 'song_123',
      providerId: 'jiosaavn',
      title: 'Kesariya',
      artist: 'Arijit Singh',
      imageUrl: 'https://example.com/cover.jpg',
      duration: 210,
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing required fields', () => {
    const result = likeSongSchema.safeParse({
      songId: 'song_123',
    });
    expect(result.success).toBe(false);
  });
});

describe('checkLikesQuerySchema', () => {
  it('accepts songIds string', () => {
    const result = checkLikesQuerySchema.safeParse({
      songIds: 'a,b,c',
    });
    expect(result.success).toBe(true);
  });
});

describe('syncLikesSchema', () => {
  it('accepts empty or populated pending sync items', () => {
    const result = syncLikesSchema.safeParse({
      pendingLikes: [
        {
          songId: 's1',
          providerId: 'jiosaavn',
          title: 'Song 1',
          artist: 'Artist 1',
          likedAt: Date.now(),
        },
      ],
      pendingUnlikes: ['s2'],
    });
    expect(result.success).toBe(true);
  });
});

describe('createPlaylistSchema', () => {
  it('accepts valid playlist data', () => {
    const result = createPlaylistSchema.safeParse({
      name: 'Chill Vibes',
      description: 'Relaxing songs',
    });
    expect(result.success).toBe(true);
  });

  it('rejects empty name', () => {
    const result = createPlaylistSchema.safeParse({
      name: '',
    });
    expect(result.success).toBe(false);
  });
});

describe('reorderPlaylistSongsSchema', () => {
  it('accepts non-empty list of IDs', () => {
    const result = reorderPlaylistSongsSchema.safeParse({
      songIds: ['s1', 's2', 's3'],
    });
    expect(result.success).toBe(true);
  });

  it('rejects empty list', () => {
    const result = reorderPlaylistSongsSchema.safeParse({
      songIds: [],
    });
    expect(result.success).toBe(false);
  });
});

describe('followTeamSchema', () => {
  it('accepts valid team data', () => {
    const result = followTeamSchema.safeParse({
      teamId: 'csk',
      teamName: 'Chennai Super Kings',
      shortName: 'CSK',
      sport: 'cricket',
    });
    expect(result.success).toBe(true);
  });
});
