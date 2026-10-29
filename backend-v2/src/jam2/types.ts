import { z } from 'zod';

export const JAM_ROOM_CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

// ─── Song Schema (mirrors Android SongDto & backend Song) ─────────────
export const artistCreditSchema = z.object({
  id: z.string().max(100),
  name: z.string().max(200),
  imageUrl: z.string().max(1000).nullable().optional(),
}).strip();

export const albumRefSchema = z.object({
  id: z.string().max(100),
  title: z.string().max(300),
  imageUrl: z.string().max(1000).nullable().optional(),
}).strip();

export const songSchema = z.object({
  id: z.string().min(1).max(100),
  title: z.string().min(1).max(300),
  artists: z.array(artistCreditSchema).max(30).default([]),
  album: albumRefSchema.nullable().optional(),
  duration: z.number().int().min(0).max(86400).default(0), // in seconds
  imageUrl: z.string().max(1000).nullable().optional(),
  year: z.string().max(20).optional().default(''),
  language: z.string().max(50).optional().default(''),
  hasLyrics: z.boolean().optional().default(false),
  playCount: z.number().int().min(0).optional().default(0),
  label: z.string().max(200).optional().default(''),
  streamRef: z.string().max(500).optional().default(''),
  providerId: z.string().max(50).optional().default(''),
}).strip();

export type Song = z.infer<typeof songSchema>;

// ─── Permissions Schema ──────────────────────────────────────────────
export const DEFINED_PERM_KEYS = ['queueAdd', 'playback', 'queueManage'] as const;
export type DefinedPermKey = typeof DEFINED_PERM_KEYS[number];

export const permsSchema = z.record(z.string(), z.boolean()).refine(
  (perms) => Object.keys(perms).every((key) => DEFINED_PERM_KEYS.includes(key as DefinedPermKey)),
  { message: 'Unknown permission key in perms map' },
);

export const DEFAULT_ROOM_PERMS: Record<string, boolean> = {
  queueAdd: true,
  playback: false,
  queueManage: false,
};

// ─── Queue & Playback Models ─────────────────────────────────────────
export interface QueueItem {
  qid: string;
  song: Song;
  addedByUserId: string | null;
  addedAt: number;
  recommended: boolean;
}

export type PlaybackStatus = 'idle' | 'playing' | 'paused';

export interface NowPlaying {
  item: QueueItem | null;
  status: PlaybackStatus;
  positionMs: number;
  anchorServerTs: number;
  trackSeq: number;
}

export interface Member {
  userId: string;
  username: string;
  avatarUrl?: string;
  joinedAt: number;
  online: boolean;
  lastSeenAt: number;
  perms: Record<string, boolean>;
}

export interface Room {
  code: string;
  version: number;
  hostUserId: string;
  createdAt: number;
  members: Map<string, Member>;
  defaults: Record<string, boolean>;
  queue: QueueItem[];
  history: QueueItem[];
  nowPlaying: NowPlaying;
}

export interface RoomSnapshot {
  code: string;
  version: number;
  serverNow: number;
  hostUserId: string;
  createdAt: number;
  members: Member[];
  defaults: Record<string, boolean>;
  queue: QueueItem[];
  history: QueueItem[];
  nowPlaying: NowPlaying;
}

export function serializeRoom(room: Room, serverNow: number): RoomSnapshot {
  return {
    code: room.code,
    version: room.version,
    serverNow,
    hostUserId: room.hostUserId,
    createdAt: room.createdAt,
    members: Array.from(room.members.values()),
    defaults: { ...room.defaults },
    queue: room.queue.map((q) => ({ ...q })),
    history: room.history.map((h) => ({ ...h })),
    nowPlaying: { ...room.nowPlaying, item: room.nowPlaying.item ? { ...room.nowPlaying.item } : null },
  };
}

export function getExpectedPosition(nowPlaying: NowPlaying, now: number): number {
  if (!nowPlaying.item) return 0;
  const base =
    nowPlaying.status === 'playing'
      ? nowPlaying.positionMs + (now - nowPlaying.anchorServerTs)
      : nowPlaying.positionMs;
  const durationMs =
    nowPlaying.item.song.duration > 0
      ? nowPlaying.item.song.duration * 1000
      : Number.MAX_SAFE_INTEGER;
  return Math.max(0, Math.min(base, durationMs));
}

// ─── Error & Event Types ─────────────────────────────────────────────
export type Jam2ErrorCode =
  | 'NOT_FOUND'
  | 'NOT_MEMBER'
  | 'FORBIDDEN'
  | 'INVALID'
  | 'QUEUE_FULL'
  | 'RATE_LIMITED'
  | 'PROTOCOL';

export interface Jam2Error {
  code: Jam2ErrorCode;
  message: string;
}

export type Jam2Ack<T = Record<string, unknown>> =
  | ({ ok: true } & T)
  | { ok: false; code: Jam2ErrorCode; message: string };

export interface Jam2Tick {
  code: string;
  version: number;
  serverNow: number;
  nowPlaying: {
    status: PlaybackStatus;
    positionMs: number;
    anchorServerTs: number;
    trackSeq: number;
    qid: string | null;
  };
}

export type Jam2EventKind =
  | 'member_joined'
  | 'member_left'
  | 'kicked'
  | 'host_changed'
  | 'server_restarting'
  | 'perm_changed';

export interface Jam2Event {
  kind: Jam2EventKind;
  userId?: string;
  username?: string;
  [key: string]: unknown;
}

export interface Jam2Closed {
  reason: 'ended' | 'kicked' | 'expired';
}

// ─── Command Schemas ─────────────────────────────────────────────────
export const createPayloadSchema = z.object({
  protocol: z.number().int(),
  avatarUrl: z.string().max(1000).optional(),
}).strip();

export const joinPayloadSchema = z.object({
  protocol: z.number().int(),
  code: z.string().min(1).max(20),
  avatarUrl: z.string().max(1000).optional(),
}).strip();

export const roomCodeSchema = z.object({
  code: z.string().min(1).max(20),
}).strip();

const roomCodeField = z.string().min(1).max(20);

export const cmdBaseSchema = z.object({
  code: roomCodeField,
  type: z.string(),
});

export const playCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('play'),
});

export const pauseCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('pause'),
});

export const seekCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('seek'),
  positionMs: z.number().int().min(0),
});

export const nextCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('next'),
});

export const prevCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('prev'),
});

export const playItemCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('playItem'),
  qid: z.string().min(1).max(100),
});

export const trackEndedCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('trackEnded'),
  trackSeq: z.number().int().min(0),
});

export const queueAddCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('queueAdd'),
  song: songSchema,
  playNext: z.boolean().optional(),
});

export const queueAddManyCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('queueAddMany'),
  songs: z.array(songSchema).min(1).max(20),
});

export const promoteCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('promote'),
  qid: z.string().min(1).max(100),
});

export const queueRemoveCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('queueRemove'),
  qid: z.string().min(1).max(100),
});

export const queueMoveCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('queueMove'),
  qid: z.string().min(1).max(100),
  toIndex: z.number().int().min(0),
});

export const setMemberPermsCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('setMemberPerms'),
  userId: z.string().min(1).max(100),
  perms: permsSchema,
});

export const setDefaultsCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('setDefaults'),
  perms: permsSchema,
  applyToExisting: z.boolean().optional(),
});

export const kickCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('kick'),
  userId: z.string().min(1).max(100),
});

export const transferHostCmdSchema = z.object({
  code: roomCodeField,
  type: z.literal('transferHost'),
  userId: z.string().min(1).max(100),
});

export const clientCmdSchema = z.discriminatedUnion('type', [
  playCmdSchema,
  pauseCmdSchema,
  seekCmdSchema,
  nextCmdSchema,
  prevCmdSchema,
  playItemCmdSchema,
  trackEndedCmdSchema,
  queueAddCmdSchema,
  queueAddManyCmdSchema,
  promoteCmdSchema,
  queueRemoveCmdSchema,
  queueMoveCmdSchema,
  setMemberPermsCmdSchema,
  setDefaultsCmdSchema,
  kickCmdSchema,
  transferHostCmdSchema,
]);

export type ClientCommand = z.infer<typeof clientCmdSchema>;

// Internal commands (triggered by timers/network/presence/recommendations)
export interface RecsAddedCmd {
  type: 'recsAdded';
  forTrackSeq: number;
  songs: Song[];
}

export interface HostHandoverCmd {
  type: 'hostHandover';
  newHostUserId: string;
}

export interface MemberOnlineCmd {
  type: 'memberOnline';
  userId: string;
  username: string;
  avatarUrl?: string;
}

export interface MemberOfflineCmd {
  type: 'memberOffline';
  userId: string;
}

export interface MemberLeaveCmd {
  type: 'memberLeave';
  userId: string;
}

export type Jam2Command = ClientCommand | RecsAddedCmd | HostHandoverCmd | MemberOnlineCmd | MemberOfflineCmd | MemberLeaveCmd;

