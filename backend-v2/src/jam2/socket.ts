import { Server, Socket } from 'socket.io';
import { logger } from '../utils/logger';
import { jam2Manager } from './manager';
import { resolveSong, resolveSongOrFallback, sanitizeUrl } from './songResolver';
import { checkCommandPermission } from './permissions';
import {
  createPayloadSchema,
  joinPayloadSchema,
  roomCodeSchema,
  clientCmdSchema,
  serializeRoom,
  Jam2Ack,
} from './types';

type AckFn<T = Record<string, unknown>> = (res: Jam2Ack<T>) => void;

function safeAck<T = Record<string, unknown>>(
  ack: unknown,
  payload: Jam2Ack<T>,
): void {
  if (typeof ack === 'function') {
    try {
      (ack as AckFn<T>)(payload);
    } catch (err) {
      logger.warn({ err }, 'Error sending Socket.IO ack to client');
    }
  }
}

export function registerJam2Handlers(io: Server, socket: Socket): void {
  jam2Manager.setIO(io);

  const user = socket.data.user as { userId: string; username: string } | undefined;

  // ─── 1. jam2:create ──────────────────────────────────────────────────
  socket.on('jam2:create', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      if (!jam2Manager.checkCreateRateLimit(user.userId)) {
        return safeAck(ack, {
          ok: false,
          code: 'RATE_LIMITED',
          message: 'Too many rooms created. Please wait a minute before creating another.',
        });
      }

      const parsed = createPayloadSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid create payload: ' + parsed.error.issues[0]?.message,
        });
      }

      if (parsed.data.protocol !== 2) {
        return safeAck(ack, {
          ok: false,
          code: 'PROTOCOL',
          message: 'Unsupported protocol version. Please update your app.',
        });
      }

      // Enforce ONE active room per user
      const existingCode = jam2Manager.getActiveRoomForUser(user.userId);
      if (existingCode) {
        const userSockets = jam2Manager.getUserSockets(existingCode, user.userId);
        for (const sId of userSockets) {
          const s = io.sockets.sockets.get(sId);
          if (s) s.leave(`jam2:${existingCode}`);
        }
        jam2Manager.deregisterUserSockets(existingCode, user.userId);
        await jam2Manager.leaveRoom(existingCode, user.userId);
      }

      const cleanAvatarUrl = sanitizeUrl(parsed.data.avatarUrl) ?? undefined;
      const room = await jam2Manager.createRoom(
        user.userId,
        user.username,
        cleanAvatarUrl,
      );

      socket.join(`jam2:${room.code}`);
      jam2Manager.registerSocket(socket.id, room.code, user.userId, user.username);

      const snapshot = serializeRoom(room, Date.now());
      safeAck(ack, { ok: true, room: snapshot });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:create');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error creating room' });
    }
  });

  // ─── 2. jam2:join ────────────────────────────────────────────────────
  socket.on('jam2:join', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      // Check failed join rate limit
      if (!jam2Manager.checkJoinFailRateLimit(user.userId)) {
        return safeAck(ack, {
          ok: false,
          code: 'RATE_LIMITED',
          message: 'Too many failed join attempts. Please wait a minute before retrying.',
        });
      }

      const parsed = joinPayloadSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid join payload: ' + parsed.error.issues[0]?.message,
        });
      }

      if (parsed.data.protocol !== 2) {
        return safeAck(ack, {
          ok: false,
          code: 'PROTOCOL',
          message: 'Unsupported protocol version. Please update your app.',
        });
      }

      const normalizedCode = parsed.data.code.trim().toUpperCase();

      // Track existing room to leave ONLY after successful join (Task F7)
      const existingCode = jam2Manager.getActiveRoomForUser(user.userId);

      const cleanAvatarUrl = sanitizeUrl(parsed.data.avatarUrl) ?? undefined;
      const res = await jam2Manager.joinRoom(
        normalizedCode,
        user.userId,
        user.username,
        cleanAvatarUrl,
      );

      if ('error' in res) {
        if (res.error.code === 'NOT_FOUND') {
          jam2Manager.recordJoinFailure(user.userId);
        }
        return safeAck(ack, {
          ok: false,
          code: res.error.code,
          message: res.error.message,
        });
      }

      // Enforce ONE active room per user - leave old room after successful join
      if (existingCode && existingCode !== normalizedCode) {
        const userSockets = jam2Manager.getUserSockets(existingCode, user.userId);
        for (const sId of userSockets) {
          const s = io.sockets.sockets.get(sId);
          if (s) s.leave(`jam2:${existingCode}`);
        }
        jam2Manager.deregisterUserSockets(existingCode, user.userId);
        await jam2Manager.leaveRoom(existingCode, user.userId);
      }

      socket.join(`jam2:${normalizedCode}`);
      jam2Manager.registerSocket(socket.id, normalizedCode, user.userId, user.username);

      const snapshot = serializeRoom(res.room, Date.now());
      safeAck(ack, { ok: true, room: snapshot });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:join');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error joining room' });
    }
  });

  // ─── 3. jam2:leave ───────────────────────────────────────────────────
  socket.on('jam2:leave', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      const parsed = roomCodeSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid leave payload',
        });
      }

      const code = parsed.data.code.trim().toUpperCase();
      const userSockets = jam2Manager.getUserSockets(code, user.userId);
      for (const sId of userSockets) {
        const s = io.sockets.sockets.get(sId);
        if (s) s.leave(`jam2:${code}`);
      }
      socket.leave(`jam2:${code}`);
      jam2Manager.deregisterUserSockets(code, user.userId);
      const res = await jam2Manager.leaveRoom(code, user.userId);

      if (!res.success && res.error) {
        return safeAck(ack, {
          ok: false,
          code: res.error.code,
          message: res.error.message,
        });
      }

      safeAck(ack, { ok: true });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:leave');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error leaving room' });
    }
  });

  // ─── 4. jam2:end ─────────────────────────────────────────────────────
  socket.on('jam2:end', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      const parsed = roomCodeSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid end payload',
        });
      }

      const code = parsed.data.code.trim().toUpperCase();
      const res = await jam2Manager.endRoom(code, user.userId);

      if (!res.success && res.error) {
        return safeAck(ack, {
          ok: false,
          code: res.error.code,
          message: res.error.message,
        });
      }

      safeAck(ack, { ok: true });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:end');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error ending room' });
    }
  });

  // ─── 5. jam2:time ────────────────────────────────────────────────────
  socket.on('jam2:time', (_rawPayload: unknown, ack?: unknown) => {
    try {
      if (!jam2Manager.checkTimeRateLimit(socket.id)) {
        return safeAck(ack, {
          ok: false,
          code: 'RATE_LIMITED',
          message: 'Too many time sync requests. Please slow down.',
        });
      }

      safeAck(ack, { ok: true, serverNow: Date.now() });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:time');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error' });
    }
  });

  // ─── 5b. jam2:sync ───────────────────────────────────────────────────
  socket.on('jam2:sync', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      const parsed = roomCodeSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid sync payload',
        });
      }

      const code = parsed.data.code.trim().toUpperCase();
      const room = await jam2Manager.getRoom(code);
      if (!room) {
        return safeAck(ack, {
          ok: false,
          code: 'NOT_FOUND',
          message: 'Room not found',
        });
      }

      if (!room.members.has(user.userId)) {
        return safeAck(ack, {
          ok: false,
          code: 'NOT_MEMBER',
          message: 'You are not a member of this Jam session',
        });
      }

      const snapshot = serializeRoom(room, Date.now());
      safeAck(ack, { ok: true, room: snapshot });
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:sync');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error syncing room' });
    }
  });

  // ─── 6. jam2:cmd ─────────────────────────────────────────────────────
  socket.on('jam2:cmd', async (rawPayload: unknown, ack?: unknown) => {
    try {
      if (!user) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: 'Unauthorized: missing user context',
        });
      }

      // Check command rate limit
      if (!jam2Manager.checkCmdRateLimit(socket.id)) {
        return safeAck(ack, {
          ok: false,
          code: 'RATE_LIMITED',
          message: 'Too many commands sent. Please slow down.',
        });
      }

      const parsed = clientCmdSchema.safeParse(rawPayload);
      if (!parsed.success) {
        return safeAck(ack, {
          ok: false,
          code: 'INVALID',
          message: 'Invalid command: ' + parsed.error.issues[0]?.message,
        });
      }

      const cmd = parsed.data;
      const normalizedCode = cmd.code.trim().toUpperCase();

      // Fast permission precheck before resolving external metadata (Task F11.b)
      const preRoom = await jam2Manager.getRoom(normalizedCode);
      if (!preRoom) {
        return safeAck(ack, {
          ok: false,
          code: 'NOT_FOUND',
          message: 'Room not found',
        });
      }
      const permCheck = checkCommandPermission(preRoom, user.userId, cmd);
      if (!permCheck.allowed) {
        return safeAck(ack, {
          ok: false,
          code: 'FORBIDDEN',
          message: permCheck.reason || 'Permission denied',
        });
      }

      let skippedUnresolved = 0;
      // Resolve song metadata from provider outside room mutex (Task A5, F3, F14)
      if (cmd.type === 'queueAdd') {
        const resolved = await resolveSongOrFallback(cmd.song);
        if (!resolved) {
          const provider = cmd.song.providerId || 'music provider';
          return safeAck(ack, {
            ok: false,
            code: 'INVALID',
            message: `Couldn't load this song from ${provider}`,
          });
        }
        cmd.song = resolved;
      } else if (cmd.type === 'queueAddMany') {
        const resolvedSongs: typeof cmd.songs = [];
        const chunkSize = 5;
        for (let i = 0; i < cmd.songs.length; i += chunkSize) {
          const chunk = cmd.songs.slice(i, i + chunkSize);
          const results = await Promise.all(
            chunk.map((s) => resolveSongOrFallback(s)),
          );
          for (let j = 0; j < chunk.length; j++) {
            const resolved = results[j];
            if (resolved) {
              resolvedSongs.push(resolved);
            } else {
              skippedUnresolved++;
            }
          }
        }
        if (resolvedSongs.length === 0) {
          return safeAck(ack, {
            ok: false,
            code: 'INVALID',
            message: 'None of the requested songs could be resolved',
          });
        }
        cmd.songs = resolvedSongs;
      }

      const res = await jam2Manager.executeCommand(
        normalizedCode,
        cmd,
        user.userId,
      );

      if ('error' in res) {
        return safeAck(ack, {
          ok: false,
          code: res.error.code,
          message: res.error.message,
        });
      }

      // Special handling for kick: notify the kicked user and remove from room
      if (cmd.type === 'kick') {
        const kickedUserId = cmd.userId;
        const socketsInRoom = await io.in(`jam2:${normalizedCode}`).fetchSockets();
        for (const s of socketsInRoom) {
          if (s.data?.user?.userId === kickedUserId) {
            s.emit('jam2:closed', { reason: 'kicked' });
            s.leave(`jam2:${normalizedCode}`);
          }
        }
        jam2Manager.deregisterUserSockets(normalizedCode, kickedUserId);
      }

      if (cmd.type === 'trackEnded') {
        safeAck(ack, {
          ok: true,
          applied: res.applied ?? false,
          version: res.room.version,
        });
      } else if (cmd.type === 'queueAddMany') {
        safeAck(ack, {
          ok: true,
          added: res.added ?? 1,
          skipped: (res.skipped ?? 0) + (skippedUnresolved ?? 0),
          skippedDuplicate: res.skippedDuplicate ?? 0,
          skippedFull: res.skippedFull ?? 0,
          skippedUnresolved: skippedUnresolved ?? 0,
          version: res.room.version,
        });
      } else if (cmd.type === 'queueAdd') {
        safeAck(ack, {
          ok: true,
          added: res.added ?? 1,
          skipped: res.skipped ?? 0,
          skippedDuplicate: res.skippedDuplicate ?? 0,
          skippedFull: res.skippedFull ?? 0,
          version: res.room.version,
        });
      } else {
        safeAck(ack, { ok: true, version: res.room.version });
      }
    } catch (err) {
      logger.error({ err, socketId: socket.id }, 'Error in jam2:cmd');
      safeAck(ack, { ok: false, code: 'INVALID', message: 'Internal server error processing command' });
    }
  });

  // ─── Disconnect ──────────────────────────────────────────────────────
  socket.on('disconnect', () => {
    jam2Manager.handleSocketDisconnect(socket.id);
  });
}

