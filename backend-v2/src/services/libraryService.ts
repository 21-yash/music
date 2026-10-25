import { Types } from 'mongoose';
import { LikedSong, Playlist, FollowedTeam } from '../models';
import { NotFoundError, BadRequestError } from '../utils/errors';
import { upgradeImageUrl } from '../providers/music/jiosaavn/jiosaavnMapper';

// ─── Liked Songs ─────────────────────────────────────────────────────────────

export interface LikeSongInput {
  songId: string;
  providerId: string;
  title: string;
  artist: string;
  imageUrl?: string | null;
  duration?: number;
}

export interface SyncLikeItem extends LikeSongInput {
  likedAt?: number;
}

export async function getLikedSongs(
  userId: string,
  page = 1,
  limit = 50,
) {
  const safePage = Math.max(1, page);
  const safeLimit = Math.min(100, Math.max(1, limit));
  const skip = (safePage - 1) * safeLimit;

  const userObjectId = new Types.ObjectId(userId);

  const [songs, total] = await Promise.all([
    LikedSong.find({ userId: userObjectId })
      .sort({ likedAt: -1 })
      .skip(skip)
      .limit(safeLimit)
      .lean(),
    LikedSong.countDocuments({ userId: userObjectId }),
  ]);

  for (const s of songs) {
    (s as any).imageUrl = upgradeImageUrl(s.imageUrl);
  }

  return {
    songs,
    total,
    page: safePage,
    limit: safeLimit,
    hasMore: skip + songs.length < total,
  };
}

export async function likeSong(userId: string, data: LikeSongInput) {
  const userObjectId = new Types.ObjectId(userId);

  const likedSong = await LikedSong.findOneAndUpdate(
    {
      userId: userObjectId,
      songId: data.songId,
      providerId: data.providerId,
    },
    {
      userId: userObjectId,
      songId: data.songId,
      providerId: data.providerId,
      title: data.title,
      artist: data.artist,
      imageUrl: upgradeImageUrl(data.imageUrl),
      duration: data.duration ?? 0,
      likedAt: new Date(),
    },
    {
      upsert: true,
      new: true,
      setDefaultsOnInsert: true,
    },
  );

  return likedSong;
}

export async function unlikeSong(
  userId: string,
  songId: string,
  providerId?: string,
) {
  const userObjectId = new Types.ObjectId(userId);
  const query: Record<string, unknown> = {
    userId: userObjectId,
    songId,
  };
  if (providerId) {
    query.providerId = providerId;
  }

  const result = await LikedSong.deleteMany(query);
  return {
    success: true,
    deletedCount: result.deletedCount,
  };
}

export async function checkLikedSongs(userId: string, songIds: string[]) {
  const userObjectId = new Types.ObjectId(userId);
  const matches = await LikedSong.find({
    userId: userObjectId,
    songId: { $in: songIds },
  })
    .select('songId -_id')
    .lean();

  const likedSet = new Set(matches.map((m) => m.songId));
  const result: Record<string, boolean> = {};
  for (const id of songIds) {
    result[id] = likedSet.has(id);
  }

  return {
    likedMap: result,
    likedSongIds: Array.from(likedSet),
  };
}

export async function syncLikes(
  userId: string,
  pendingLikes: SyncLikeItem[] = [],
  pendingUnlikes: string[] = [],
  _since?: string | number,
) {
  const userObjectId = new Types.ObjectId(userId);

  // 1. Process pending unlikes
  if (pendingUnlikes.length > 0) {
    await LikedSong.deleteMany({
      userId: userObjectId,
      songId: { $in: pendingUnlikes },
    });
  }

  // 2. Process pending likes
  if (pendingLikes.length > 0) {
    const operations = pendingLikes.map((item) => ({
      updateOne: {
        filter: {
          userId: userObjectId,
          songId: item.songId,
          providerId: item.providerId,
        },
        update: {
          $set: {
            userId: userObjectId,
            songId: item.songId,
            providerId: item.providerId,
            title: item.title,
            artist: item.artist,
            imageUrl: upgradeImageUrl(item.imageUrl),
            duration: item.duration ?? 0,
            likedAt: item.likedAt ? new Date(item.likedAt) : new Date(),
          },
        },
        upsert: true,
      },
    }));
    await LikedSong.bulkWrite(operations);
  }

  // 3. Return full merged list
  const allLiked = await LikedSong.find({ userId: userObjectId })
    .sort({ likedAt: -1 })
    .lean();

  for (const s of allLiked) {
    (s as any).imageUrl = upgradeImageUrl(s.imageUrl);
  }

  return {
    likes: allLiked,
    serverTime: Date.now(),
  };
}

// ─── Playlists ───────────────────────────────────────────────────────────────

export async function getPlaylists(userId: string) {
  const userObjectId = new Types.ObjectId(userId);
  const playlists = await Playlist.find({ userId: userObjectId })
    .sort({ updatedAt: -1 })
    .lean();

  return playlists.map((p) => ({
    id: p._id.toString(),
    name: p.name,
    description: p.description,
    imageUrl: upgradeImageUrl(p.imageUrl ?? (p.songs.length > 0 ? p.songs[0].imageUrl : null)),
    songCount: p.songs.length,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  }));
}

export async function createPlaylist(
  userId: string,
  data: { name: string; description?: string; imageUrl?: string | null },
) {
  const userObjectId = new Types.ObjectId(userId);
  const playlist = new Playlist({
    userId: userObjectId,
    name: data.name,
    description: data.description ?? '',
    imageUrl: upgradeImageUrl(data.imageUrl),
    songs: [],
  });

  await playlist.save();
  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: playlist.imageUrl,
    songs: playlist.songs,
    songCount: 0,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

export async function getPlaylistById(userId: string, playlistId: string) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const playlist = await Playlist.findOne({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  }).lean();

  if (!playlist) {
    throw new NotFoundError('Playlist');
  }

  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: upgradeImageUrl(
      playlist.imageUrl ??
      (playlist.songs.length > 0 ? playlist.songs[0].imageUrl : null),
    ),
    songs: playlist.songs.map((s) => ({
      ...s,
      imageUrl: upgradeImageUrl(s.imageUrl),
    })),
    songCount: playlist.songs.length,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

export async function updatePlaylist(
  userId: string,
  playlistId: string,
  data: { name?: string; description?: string; imageUrl?: string | null },
) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const playlist = await Playlist.findOne({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  });

  if (!playlist) {
    throw new NotFoundError('Playlist');
  }

  if (data.name !== undefined) playlist.name = data.name;
  if (data.description !== undefined) playlist.description = data.description;
  if (data.imageUrl !== undefined) playlist.imageUrl = upgradeImageUrl(data.imageUrl);

  await playlist.save();

  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: playlist.imageUrl,
    songs: playlist.songs,
    songCount: playlist.songs.length,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

export async function deletePlaylist(userId: string, playlistId: string) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const result = await Playlist.findOneAndDelete({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  });

  if (!result) {
    throw new NotFoundError('Playlist');
  }

  return { success: true };
}

export async function addSongToPlaylist(
  userId: string,
  playlistId: string,
  songData: LikeSongInput,
) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const playlist = await Playlist.findOne({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  });

  if (!playlist) {
    throw new NotFoundError('Playlist');
  }

  const exists = playlist.songs.some(
    (s) => s.songId === songData.songId && s.providerId === songData.providerId,
  );
  if (exists) {
    throw new BadRequestError('Song already in playlist');
  }

  const newPosition = playlist.songs.length;
  playlist.songs.push({
    songId: songData.songId,
    providerId: songData.providerId,
    title: songData.title,
    artist: songData.artist,
    imageUrl: upgradeImageUrl(songData.imageUrl),
    duration: songData.duration ?? 0,
    addedAt: new Date(),
    position: newPosition,
  });

  if (!playlist.imageUrl && songData.imageUrl) {
    playlist.imageUrl = upgradeImageUrl(songData.imageUrl);
  }

  await playlist.save();

  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: playlist.imageUrl,
    songs: playlist.songs,
    songCount: playlist.songs.length,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

export async function removeSongFromPlaylist(
  userId: string,
  playlistId: string,
  songId: string,
) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const playlist = await Playlist.findOne({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  });

  if (!playlist) {
    throw new NotFoundError('Playlist');
  }

  const initialCount = playlist.songs.length;
  playlist.songs = playlist.songs.filter((s) => s.songId !== songId);

  if (playlist.songs.length === initialCount) {
    throw new NotFoundError('Song in playlist');
  }

  // Re-index positions
  playlist.songs.forEach((s, idx) => {
    s.position = idx;
  });

  await playlist.save();

  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: playlist.imageUrl,
    songs: playlist.songs,
    songCount: playlist.songs.length,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

export async function reorderPlaylistSongs(
  userId: string,
  playlistId: string,
  songIds: string[],
) {
  if (!Types.ObjectId.isValid(playlistId)) {
    throw new BadRequestError('Invalid playlist ID');
  }

  const userObjectId = new Types.ObjectId(userId);
  const playlist = await Playlist.findOne({
    _id: new Types.ObjectId(playlistId),
    userId: userObjectId,
  });

  if (!playlist) {
    throw new NotFoundError('Playlist');
  }

  const songMap = new Map(playlist.songs.map((s) => [s.songId, s]));
  const reordered: typeof playlist.songs = [];

  for (let i = 0; i < songIds.length; i++) {
    const s = songMap.get(songIds[i]);
    if (s) {
      s.position = i;
      reordered.push(s);
      songMap.delete(songIds[i]);
    }
  }

  // Append any songs not explicitly mentioned in the reorder list
  for (const remaining of songMap.values()) {
    remaining.position = reordered.length;
    reordered.push(remaining);
  }

  playlist.songs = reordered;
  await playlist.save();

  return {
    id: playlist._id.toString(),
    name: playlist.name,
    description: playlist.description,
    imageUrl: playlist.imageUrl,
    songs: playlist.songs,
    songCount: playlist.songs.length,
    createdAt: playlist.createdAt,
    updatedAt: playlist.updatedAt,
  };
}

// ─── Followed Teams ──────────────────────────────────────────────────────────

export interface FollowTeamInput {
  teamId: string;
  teamName: string;
  shortName: string;
  imageUrl?: string | null;
  sport?: string;
}

export async function getFollowedTeams(userId: string) {
  const userObjectId = new Types.ObjectId(userId);
  const teams = await FollowedTeam.find({ userId: userObjectId })
    .sort({ followedAt: -1 })
    .lean();

  return teams.map((t) => ({
    teamId: t.teamId,
    teamName: t.teamName,
    shortName: t.shortName,
    imageUrl: t.imageUrl,
    sport: t.sport,
    followedAt: t.followedAt,
  }));
}

export async function followTeam(userId: string, data: FollowTeamInput) {
  const userObjectId = new Types.ObjectId(userId);

  const team = await FollowedTeam.findOneAndUpdate(
    {
      userId: userObjectId,
      teamId: data.teamId,
    },
    {
      userId: userObjectId,
      teamId: data.teamId,
      teamName: data.teamName,
      shortName: data.shortName,
      imageUrl: data.imageUrl ?? null,
      sport: data.sport || 'cricket',
      followedAt: new Date(),
    },
    {
      upsert: true,
      new: true,
      setDefaultsOnInsert: true,
    },
  );

  return {
    teamId: team.teamId,
    teamName: team.teamName,
    shortName: team.shortName,
    imageUrl: team.imageUrl,
    sport: team.sport,
    followedAt: team.followedAt,
  };
}

export async function unfollowTeam(userId: string, teamId: string) {
  const userObjectId = new Types.ObjectId(userId);
  const result = await FollowedTeam.findOneAndDelete({
    userId: userObjectId,
    teamId,
  });

  return {
    success: true,
    unfollowed: !!result,
  };
}

export async function checkFollowedTeams(userId: string, teamIds: string[]) {
  const userObjectId = new Types.ObjectId(userId);
  const matches = await FollowedTeam.find({
    userId: userObjectId,
    teamId: { $in: teamIds },
  })
    .select('teamId -_id')
    .lean();

  const followedSet = new Set(matches.map((m) => m.teamId));
  const result: Record<string, boolean> = {};
  for (const id of teamIds) {
    result[id] = followedSet.has(id);
  }

  return {
    followedMap: result,
    followedTeamIds: Array.from(followedSet),
  };
}
