import mongoose, { Document, Schema, Types } from 'mongoose';

export interface IPlaylistSong {
  songId: string;
  providerId: string;
  title: string;
  artist: string;
  imageUrl: string | null;
  duration: number;
  addedAt: Date;
  position: number;
}

export interface IPlaylist extends Document {
  userId: Types.ObjectId;
  name: string;
  description: string;
  imageUrl: string | null;
  songs: IPlaylistSong[];
  createdAt: Date;
  updatedAt: Date;
}

const playlistSongSchema = new Schema<IPlaylistSong>(
  {
    songId: {
      type: String,
      required: true,
      trim: true,
    },
    providerId: {
      type: String,
      required: true,
      trim: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
    },
    artist: {
      type: String,
      required: true,
      trim: true,
    },
    imageUrl: {
      type: String,
      default: null,
      trim: true,
    },
    duration: {
      type: Number,
      default: 0,
    },
    addedAt: {
      type: Date,
      default: Date.now,
    },
    position: {
      type: Number,
      default: 0,
    },
  },
  { _id: true },
);

const playlistSchema = new Schema<IPlaylist>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    name: {
      type: String,
      required: true,
      trim: true,
      maxlength: 100,
    },
    description: {
      type: String,
      default: '',
      trim: true,
      maxlength: 500,
    },
    imageUrl: {
      type: String,
      default: null,
      trim: true,
    },
    songs: {
      type: [playlistSongSchema],
      default: [],
    },
  },
  {
    timestamps: true,
  },
);

playlistSchema.index({ userId: 1, updatedAt: -1 });

export const Playlist = mongoose.model<IPlaylist>('Playlist', playlistSchema);
