import { Socket } from 'socket.io';
import { logger } from '../utils/logger';
import { getMatchDetails } from '../services/sportsService';

/**
 * Register Socket.IO event handlers for sports/cricket.
 */
export function registerSportsHandlers(socket: Socket) {
  // Join a specific match room for live updates
  socket.on('join_match', async (payload: { matchId: string }) => {
    const { matchId } = payload;
    if (!matchId) return;

    const roomName = `match:${matchId}`;
    socket.join(roomName);
    logger.info({ socketId: socket.id, matchId }, 'Client joined match room');

    // Immediately push the current cached match details so the UI renders instantly.
    // The worker will push updates after this.
    try {
      // Don't force refresh here to keep join latency ultra-low.
      // The worker will force refresh on its next tick.
      const data = await getMatchDetails(matchId, false);
      if (data) {
        socket.emit('match_update', data);
      }
    } catch (error) {
      logger.error({ error, matchId }, 'Failed to fetch initial match state on join');
    }
  });

  // Leave a specific match room
  socket.on('leave_match', (payload: { matchId: string }) => {
    const { matchId } = payload;
    if (!matchId) return;

    const roomName = `match:${matchId}`;
    socket.leave(roomName);
    logger.info({ socketId: socket.id, matchId }, 'Client left match room');
  });
}
