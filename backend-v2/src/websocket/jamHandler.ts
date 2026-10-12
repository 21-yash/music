import { Server, Socket } from 'socket.io';
import { logger } from '../utils/logger';

export interface JamParticipant {
  socketId: string;
  userId: string;
  username: string;
  avatarUrl?: string;
  isHost: boolean;
}

export interface JamSession {
  code: string;
  hostSocketId: string;
  hostUserId: string;
  hostName: string;
  allowAttendeeReorder: boolean;
  participants: Map<string, JamParticipant>; // socketId -> JamParticipant
  currentSong: any | null;
  isPlaying: boolean;
  positionMs: number;
  lastSyncTimestamp: number;
  queue: any[];
}

// In-memory sessions store (can be backed by Redis in production)
const activeSessions = new Map<string, JamSession>();

// Map socketId -> Set<code string>
const socketToSessions = new Map<string, Set<string>>();

function generateRoomCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // exclude confusing chars (I, 1, O, 0)
  for (let attempt = 0; attempt < 100; attempt++) {
    let code = '';
    for (let i = 0; i < 4; i++) {
      code += chars[Math.floor(Math.random() * chars.length)];
    }
    if (!activeSessions.has(code)) {
      return code;
    }
  }
  return `J${Date.now().toString(36).slice(-3).toUpperCase()}`;
}

function serializeSession(session: JamSession) {
  return {
    code: session.code,
    hostUserId: session.hostUserId,
    hostName: session.hostName,
    allowAttendeeReorder: session.allowAttendeeReorder,
    participants: Array.from(session.participants.values()),
    currentSong: session.currentSong,
    isPlaying: session.isPlaying,
    positionMs: session.positionMs,
    lastSyncTimestamp: session.lastSyncTimestamp,
    queue: session.queue,
  };
}

export function registerJamHandlers(io: Server, socket: Socket) {
  // ─── Create Jam Session ─────────────────────────────────────────────
  socket.on('jam:create', (payload: { userId: string; username: string; avatarUrl?: string }) => {
    try {
      const code = generateRoomCode();
      const hostUser: JamParticipant = {
        socketId: socket.id,
        userId: payload.userId || socket.id,
        username: payload.username || 'Host',
        avatarUrl: payload.avatarUrl,
        isHost: true,
      };

      const session: JamSession = {
        code,
        hostSocketId: socket.id,
        hostUserId: hostUser.userId,
        hostName: hostUser.username,
        allowAttendeeReorder: false, // by default, only host can reorder
        participants: new Map([[socket.id, hostUser]]),
        currentSong: null,
        isPlaying: false,
        positionMs: 0,
        lastSyncTimestamp: Date.now(),
        queue: [],
      };

      activeSessions.set(code, session);
      if (!socketToSessions.has(socket.id)) {
        socketToSessions.set(socket.id, new Set());
      }
      socketToSessions.get(socket.id)!.add(code);

      const roomName = `jam:${code}`;
      socket.join(roomName);

      logger.info({ code, host: hostUser.username }, 'Jam session created');
      socket.emit('jam:created', { session: serializeSession(session) });
    } catch (err) {
      logger.error({ err }, 'Failed to create jam session');
      socket.emit('jam:error', { message: 'Failed to create jam session' });
    }
  });

  // ─── Join Jam Session ───────────────────────────────────────────────
  socket.on('jam:join', (payload: { code: string; userId: string; username: string; avatarUrl?: string }) => {
    try {
      const code = (payload.code || '').trim().toUpperCase();
      const session = activeSessions.get(code);

      if (!session) {
        socket.emit('jam:error', { message: 'Invalid or expired room code' });
        return;
      }

      const participant: JamParticipant = {
        socketId: socket.id,
        userId: payload.userId || socket.id,
        username: payload.username || 'Guest',
        avatarUrl: payload.avatarUrl,
        isHost: session.hostSocketId === socket.id,
      };

      session.participants.set(socket.id, participant);
      if (!socketToSessions.has(socket.id)) {
        socketToSessions.set(socket.id, new Set());
      }
      socketToSessions.get(socket.id)!.add(code);

      const roomName = `jam:${code}`;
      socket.join(roomName);

      logger.info({ code, user: participant.username }, 'User joined jam session');

      // Send initial full session state to joiner
      socket.emit('jam:joined', { session: serializeSession(session) });

      // Broadcast participant list to all members in the room
      io.to(roomName).emit('jam:participants_updated', {
        participants: Array.from(session.participants.values()),
      });
    } catch (err) {
      logger.error({ err }, 'Failed to join jam session');
      socket.emit('jam:error', { message: 'Failed to join jam session' });
    }
  });

  // ─── Sync Playback (Host Only) ──────────────────────────────────────
  socket.on('jam:sync_playback', (payload: { code: string; currentSong: any; isPlaying: boolean; positionMs: number }) => {
    const code = (payload.code || '').trim().toUpperCase();
    const session = activeSessions.get(code);
    if (!session) return;

    // Only host can control/sync playback
    if (session.hostSocketId !== socket.id) {
      socket.emit('jam:error', { message: 'Only the host can control playback' });
      return;
    }

    session.currentSong = payload.currentSong ?? session.currentSong;
    session.isPlaying = payload.isPlaying;
    session.positionMs = payload.positionMs;
    session.lastSyncTimestamp = Date.now();

    // Broadcast to attendees
    socket.to(`jam:${code}`).emit('jam:playback_updated', {
      currentSong: session.currentSong,
      isPlaying: session.isPlaying,
      positionMs: session.positionMs,
      timestamp: session.lastSyncTimestamp,
    });
  });

  // ─── Add to Shared Queue (Any participant) ──────────────────────────
  socket.on('jam:add_to_queue', (payload: { code: string; song: any }) => {
    const code = (payload.code || '').trim().toUpperCase();
    const session = activeSessions.get(code);
    if (!session || !payload.song) return;

    session.queue.push(payload.song);
    logger.info({ code, song: payload.song.title }, 'Song added to jam queue');

    io.to(`jam:${code}`).emit('jam:queue_updated', { queue: session.queue });
  });

  // ─── Remove from Queue (Host or with permission) ────────────────────
  socket.on('jam:remove_from_queue', (payload: { code: string; index: number }) => {
    const code = (payload.code || '').trim().toUpperCase();
    const session = activeSessions.get(code);
    if (!session) return;

    const isHost = session.hostSocketId === socket.id;
    if (!isHost && !session.allowAttendeeReorder) {
      socket.emit('jam:error', { message: 'Only host can remove songs from queue' });
      return;
    }

    if (payload.index >= 0 && payload.index < session.queue.length) {
      session.queue.splice(payload.index, 1);
      io.to(`jam:${code}`).emit('jam:queue_updated', { queue: session.queue });
    }
  });

  // ─── Reorder Queue (Host only, or attendees if permitted) ───────────
  socket.on('jam:reorder_queue', (payload: { code: string; fromIndex: number; toIndex: number }) => {
    const code = (payload.code || '').trim().toUpperCase();
    const session = activeSessions.get(code);
    if (!session) return;

    const isHost = session.hostSocketId === socket.id;
    if (!isHost && !session.allowAttendeeReorder) {
      socket.emit('jam:error', { message: 'Only the host can reorder the queue' });
      return;
    }

    const { fromIndex, toIndex } = payload;
    if (
      fromIndex >= 0 &&
      fromIndex < session.queue.length &&
      toIndex >= 0 &&
      toIndex < session.queue.length
    ) {
      const [moved] = session.queue.splice(fromIndex, 1);
      session.queue.splice(toIndex, 0, moved);
      io.to(`jam:${code}`).emit('jam:queue_updated', { queue: session.queue });
    }
  });

  // ─── Set Permissions (Host Only) ────────────────────────────────────
  socket.on('jam:set_permissions', (payload: { code: string; allowAttendeeReorder: boolean }) => {
    const code = (payload.code || '').trim().toUpperCase();
    const session = activeSessions.get(code);
    if (!session || session.hostSocketId !== socket.id) return;

    session.allowAttendeeReorder = payload.allowAttendeeReorder;
    io.to(`jam:${code}`).emit('jam:permissions_updated', {
      allowAttendeeReorder: session.allowAttendeeReorder,
    });
  });

  // ─── Leave Jam Session ──────────────────────────────────────────────
  socket.on('jam:leave', (payload: { code: string }) => {
    const code = (payload.code || '').trim().toUpperCase();
    handleUserLeave(io, socket, code);
  });

  // ─── Socket Disconnect cleanup ──────────────────────────────────────
  socket.on('disconnect', () => {
    const userCodes = socketToSessions.get(socket.id);
    if (userCodes) {
      for (const code of userCodes) {
        handleUserLeave(io, socket, code);
      }
      socketToSessions.delete(socket.id);
    }
  });
}

function handleUserLeave(io: Server, socket: Socket, code: string) {
  const session = activeSessions.get(code);
  if (!session) return;

  session.participants.delete(socket.id);
  socket.leave(`jam:${code}`);

  const userCodes = socketToSessions.get(socket.id);
  if (userCodes) {
    userCodes.delete(code);
  }

  // If host leaves and other participants remain, promote next participant to host
  if (session.hostSocketId === socket.id) {
    if (session.participants.size > 0) {
      const nextHostEntry = session.participants.entries().next().value;
      if (nextHostEntry) {
        const [nextSocketId, nextParticipant] = nextHostEntry;
        nextParticipant.isHost = true;
        session.hostSocketId = nextSocketId;
        session.hostUserId = nextParticipant.userId;
        session.hostName = nextParticipant.username;
        logger.info({ code, newHost: nextParticipant.username }, 'Promoted new host for jam session');

        io.to(`jam:${code}`).emit('jam:host_changed', {
          hostUserId: session.hostUserId,
          hostName: session.hostName,
        });
      }
    } else {
      // Room empty, delete
      activeSessions.delete(code);
      logger.info({ code }, 'Jam session closed (no participants left)');
      return;
    }
  }

  // Broadcast updated participant list
  io.to(`jam:${code}`).emit('jam:participants_updated', {
    participants: Array.from(session.participants.values()),
  });
}
