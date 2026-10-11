import { z } from 'zod';

// ─── Liked Songs ─────────────────────────────────────────────────────────────

export const likeSongSchema = z.object({
  songId: z.string().min(1, 'songId is required'),
  providerId: z.string().min(1, 'providerId is required'),
  title: z.string().min(1, 'title is required'),
  artist: z.string().min(1, 'artist is required'),
  imageUrl: z.string().nullable().optional(),
  duration: z.number().int().nonnegative().optional(),
});

export const checkLikesQuerySchema = z.object({
  songIds: z.string().min(1, 'songIds query param is required'),
});

const pendingLikeItemSchema = z.object({
  songId: z.string().min(1),
  providerId: z.string().min(1),
  title: z.string().min(1),
  artist: z.string().min(1),
  imageUrl: z.string().nullable().optional(),
  duration: z.number().int().nonnegative().optional(),
  likedAt: z.number().optional(),
});

export const syncLikesSchema = z.object({
  pendingLikes: z.array(pendingLikeItemSchema).optional().default([]),
  pendingUnlikes: z.array(z.string().min(1)).optional().default([]),
  since: z.union([z.string(), z.number()]).optional(),
});

// ─── Playlists ───────────────────────────────────────────────────────────────

export const createPlaylistSchema = z.object({
  name: z.string().min(1, 'Playlist name is required').max(100),
  description: z.string().max(500).optional().default(''),
  imageUrl: z.string().nullable().optional(),
});

export const updatePlaylistSchema = z.object({
  name: z.string().min(1).max(100).optional(),
  description: z.string().max(500).optional(),
  imageUrl: z.string().nullable().optional(),
});

export const addPlaylistSongSchema = z.object({
  songId: z.string().min(1, 'songId is required'),
  providerId: z.string().min(1, 'providerId is required'),
  title: z.string().min(1, 'title is required'),
  artist: z.string().min(1, 'artist is required'),
  imageUrl: z.string().nullable().optional(),
  duration: z.number().int().nonnegative().optional(),
});

export const reorderPlaylistSongsSchema = z.object({
  songIds: z.array(z.string().min(1)).min(1, 'songIds array must not be empty'),
});

// ─── Followed Teams ──────────────────────────────────────────────────────────

export const followTeamSchema = z.object({
  teamId: z.string().min(1, 'teamId is required'),
  teamName: z.string().min(1, 'teamName is required'),
  shortName: z.string().min(1, 'shortName is required'),
  imageUrl: z.string().nullable().optional(),
  sport: z.string().min(1).optional().default('cricket'),
});

export const checkTeamsQuerySchema = z.object({
  teamIds: z.string().min(1, 'teamIds query param is required'),
});
