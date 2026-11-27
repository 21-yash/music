import mongoose, { Document, Schema, Types } from 'mongoose';

export interface IFcmDeviceToken {
  token: string;
  platform: 'android';
  updatedAt: Date;
}

export interface ISportsNotificationPrefs {
  enabled: boolean;
  matchStartingSoon: boolean;
  matchStarted: boolean;
  matchConcluded: boolean;
  milestones: boolean;
  matchUpdates: boolean;
}

export interface INotificationSettings extends Document {
  userId: Types.ObjectId;
  fcmTokens: IFcmDeviceToken[];
  preferences: ISportsNotificationPrefs;
  createdAt: Date;
  updatedAt: Date;
}

const fcmTokenSchema = new Schema<IFcmDeviceToken>(
  {
    token: { type: String, required: true, trim: true },
    platform: { type: String, enum: ['android'], default: 'android' },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false },
);

const preferencesSchema = new Schema<ISportsNotificationPrefs>(
  {
    enabled: { type: Boolean, default: true },
    matchStartingSoon: { type: Boolean, default: true },
    matchStarted: { type: Boolean, default: true },
    matchConcluded: { type: Boolean, default: true },
    milestones: { type: Boolean, default: true },
    matchUpdates: { type: Boolean, default: true },
  },
  { _id: false },
);

const notificationSettingsSchema = new Schema<INotificationSettings>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      unique: true,
      index: true,
    },
    fcmTokens: {
      type: [fcmTokenSchema],
      default: [],
    },
    preferences: {
      type: preferencesSchema,
      default: () => ({}),
    },
  },
  {
    timestamps: true,
    versionKey: false,
  },
);

notificationSettingsSchema.index({ 'fcmTokens.token': 1 });

export const NotificationSettings = mongoose.model<INotificationSettings>(
  'NotificationSettings',
  notificationSettingsSchema,
);
