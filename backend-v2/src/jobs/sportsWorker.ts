import { Server } from 'socket.io';
import { logger } from '../utils/logger';
import { getMatchDetails } from '../services/sportsService';
import crypto from 'crypto';

/**
 * Demand-driven background worker for live sports updates.
 *
 * Lazily scrapes match details every X seconds, ONLY if there are active
 * users connected to the corresponding Socket.IO room (e.g. `match:123`).
 * 
 * If no users are connected to any match room, it does nothing and consumes 0 CPU.
 */
export class SportsWorker {
  private io: Server;
  private intervalMs: number;
  private timer: NodeJS.Timeout | null = null;
  private matchHashes: Map<string, string> = new Map();

  constructor(io: Server, intervalMs: number = 15_000) {
    this.io = io;
    this.intervalMs = intervalMs;
  }

  public start() {
    if (this.timer) return;
    this.timer = setInterval(() => this.tick(), this.intervalMs);
    logger.info({ intervalMs: this.intervalMs }, 'Sports worker started');
  }

  public stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async tick() {
    // 1. Find all active match rooms
    const activeMatchIds = this.getActiveMatchRooms();
    
    if (activeMatchIds.length === 0) {
      // Lazy scraping: do nothing if no one is listening
      return;
    }

    logger.debug({ activeMatches: activeMatchIds.length }, 'Sports worker scraping active matches');

    // 2. Fetch fresh details for each active match
    for (const matchId of activeMatchIds) {
      try {
        const result = await getMatchDetails(matchId, true); // forceRefresh = true
        if (!result) continue;

        // 3. Compare with previous state to avoid spamming identical updates
        const currentHash = this.hashPayload(result.match);
        const previousHash = this.matchHashes.get(matchId);

        if (currentHash !== previousHash) {
          this.matchHashes.set(matchId, currentHash);
          
          // 4. Broadcast to everyone in the room
          this.io.to(`match:${matchId}`).emit('match_update', result);
          logger.info({ matchId }, 'Broadcasted match update');
        }
      } catch (error) {
        logger.error({ error, matchId }, 'Worker failed to fetch match details');
      }
    }
  }

  private getActiveMatchRooms(): string[] {
    const matchIds: string[] = [];
    const rooms = this.io.sockets.adapter.rooms;
    
    for (const [roomName, clientSet] of rooms.entries()) {
      if (roomName.startsWith('match:') && clientSet.size > 0) {
        const matchId = roomName.split(':')[1];
        if (matchId) matchIds.push(matchId);
      }
    }
    
    return matchIds;
  }

  private hashPayload(payload: unknown): string {
    return crypto.createHash('sha256').update(JSON.stringify(payload)).digest('hex');
  }
}
