import mongoose, { Document, Schema, Types } from 'mongoose';

export interface IFollowedTeam extends Document {
  userId: Types.ObjectId;
  teamId: string;
  teamName: string;
  shortName: string;
  imageUrl: string | null;
  sport: string;
  followedAt: Date;
}

const followedTeamSchema = new Schema<IFollowedTeam>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    teamId: {
      type: String,
      required: true,
      trim: true,
    },
    teamName: {
      type: String,
      required: true,
      trim: true,
    },
    shortName: {
      type: String,
      required: true,
      trim: true,
    },
    imageUrl: {
      type: String,
      default: null,
      trim: true,
    },
    sport: {
      type: String,
      required: true,
      default: 'cricket',
      trim: true,
    },
    followedAt: {
      type: Date,
      default: Date.now,
    },
  },
  {
    timestamps: false,
    versionKey: false,
  },
);

followedTeamSchema.index({ userId: 1, teamId: 1 }, { unique: true });
followedTeamSchema.index({ userId: 1, followedAt: -1 });

export const FollowedTeam = mongoose.model<IFollowedTeam>('FollowedTeam', followedTeamSchema);
