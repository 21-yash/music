import mongoose, { Document, Schema, Types } from 'mongoose';

export interface ILikedSong extends Document {
  userId: Types.ObjectId;
  songId: string;
  providerId: string;
  title: string;
  artist: string;
  imageUrl: string | null;
  duration: number;
  likedAt: Date;
  albumId?: string | null;
}

const likedSongSchema = new Schema<ILikedSong>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
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
    likedAt: {
      type: Date,
      default: Date.now,
    },
    albumId: {
      type: String,
      default: null,
      trim: true,
    },
  },
  {
    timestamps: false,
    versionKey: false,
  },
);

// Compound unique index so user can't like same song twice
likedSongSchema.index({ userId: 1, songId: 1, providerId: 1 }, { unique: true });
likedSongSchema.index({ userId: 1, likedAt: -1 });

export const LikedSong = mongoose.model<ILikedSong>('LikedSong', likedSongSchema);
