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
  private lastProgress: Map<string, { balls: number; runs: number; wkts: number; inningsCount: number }> = new Map();

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

        // 3. Reject stale CDN rollback for the same active innings before broadcasting
        if (result.match.isLive && this.isStaleRollback(matchId, result.match)) {
          logger.warn({ matchId }, 'Skipped stale Cricbuzz CDN rollback snapshot');
          continue;
        }

        // 4. Compare with previous state to avoid spamming identical updates
        const currentHash = this.hashPayload(result.match);
        const previousHash = this.matchHashes.get(matchId);

        if (currentHash !== previousHash) {
          this.matchHashes.set(matchId, currentHash);
          this.recordProgress(matchId, result.match);
          
          // 5. Broadcast to everyone in the room
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

  private extractProgress(match: {
    team1Score?: string | null;
    team2Score?: string | null;
    liveScore?: { score?: string } | null;
  }): { balls: number; runs: number; wkts: number; inningsCount: number } | null {
    const s1 = (match.team1Score || '').trim();
    const s2 = (match.team2Score || '').trim();
    const countInn = (s: string) =>
      !s || /^yet to bat$/i.test(s) ? 0 : s.split(' & ').filter(Boolean).length;
    const inningsCount = countInn(s1) + countInn(s2);
    const active = match.liveScore?.score?.trim() || (countInn(s2) > 0 ? s2 : s1);
    const m = active.match(/(\d+)(?:[/-](\d+))?\s*\((\d+)(?:\.(\d+))?/);
    if (!m) return null;
    const runs = Number(m[1] || 0);
    const wkts = Number(m[2] || 0);
    const balls = Number(m[3] || 0) * 6 + Number(m[4] || 0);
    return { balls, runs, wkts, inningsCount };
  }

  private isStaleRollback(
    matchId: string,
    match: {
      team1Score?: string | null;
      team2Score?: string | null;
      liveScore?: { score?: string } | null;
    },
  ): boolean {
    const prev = this.lastProgress.get(matchId);
    const next = this.extractProgress(match);
    if (!prev || !next) return false;
    if (next.inningsCount !== prev.inningsCount) return false;
    if (next.balls < prev.balls) return true;
    if (next.wkts < prev.wkts) return true;
    if (next.balls <= prev.balls && next.runs < prev.runs) return true;
    return false;
  }

  private recordProgress(
    matchId: string,
    match: {
      team1Score?: string | null;
      team2Score?: string | null;
      liveScore?: { score?: string } | null;
    },
  ): void {
    const prog = this.extractProgress(match);
    if (prog) {
      this.lastProgress.set(matchId, prog);
    }
  }
}
