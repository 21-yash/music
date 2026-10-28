import mongoose, { Document, Schema, Types } from 'mongoose';

/**
 * Single-use Invite Code model.
 *
 * Stores the SHA-256 hash of an invite code so raw codes are never persisted.
 * Tracks usage timestamps, expiration, and the claiming user.
 */
export interface IInvite extends Document {
  codeHash: string;
  createdAt: Date;
  expiresAt: Date | null;
  usedAt: Date | null;
  usedBy: Types.ObjectId | null;
}

const inviteSchema = new Schema<IInvite>(
  {
    codeHash: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    createdAt: {
      type: Date,
      default: Date.now,
    },
    expiresAt: {
      type: Date,
      default: null,
    },
    usedAt: {
      type: Date,
      default: null,
    },
    usedBy: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      default: null,
    },
  },
  {
    timestamps: false,
  },
);

export const Invite = mongoose.model<IInvite>('Invite', inviteSchema);
