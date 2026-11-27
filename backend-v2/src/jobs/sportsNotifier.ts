import type Redis from 'ioredis';
import { MatchDetails, MatchSummary, BatsmanLive } from '../providers/sports/types';
import { getMatches, getMatchDetails } from '../services/sportsService';
import {
  ActiveSubscriber,
  getActiveSportsSubscribers,
} from '../services/notificationService';
import { sendPushToTokens, SportsPushMessage } from '../services/fcmService';
import { getRedisClient } from '../config/redis';
import { logger } from '../utils/logger';
import type { ISportsNotificationPrefs } from '../models';

const UPCOMING_SWEEP_MS = 6 * 60 * 60 * 1000; // 4 req/day (~24h Cricbuzz horizon)
const DISCOVERY_ERROR_RETRY_MS = 15 * 60 * 1000; // 15m retry if Cricbuzz fails during sweep
const SUBSCRIBER_IDLE_RETRY_MS = 5 * 60 * 1000;
const START_SOON_LEAD_MS = 30 * 60 * 1000; // T - 30 min timer
const START_SOON_EXPIRY_CUTOFF_MS = 10 * 60 * 1000; // drop starting-soon if < 10m left
const START_WATCH_LEAD_MS = 5 * 60 * 1000; // T - 5 min
const START_WATCH_CUTOFF_MS = 90 * 60 * 1000; // stop / expire started 90m after scheduled start
const START_WATCH_POLL_MS = 2.5 * 60 * 1000; // 2.5 min start watch (stops at first ball)
const LIVE_LITE_POLL_MS = 10 * 60 * 1000; // 10 min live-list poll ONLY when milestones disabled for all followers of match
const CONCLUDED_RETRY_MS = 60 * 1000; // 1 min retry if result push hit FCM transport failure
const CONCLUDED_EXPIRY_MS = 6 * 60 * 60 * 1000;
const MILESTONE_BASE_POLL_MS = 6 * 60 * 1000; // 6 min baseline (handles milestones + updates + result)
const MILESTONE_HOT_POLL_MS = 60 * 1000; // 60s when batter is on 35+ or 85+
const STUMPS_FALLBACK_POLL_MS = 30 * 60 * 1000; // 30m only if next day start unknown
const STUMPS_MORNING_POLL_MS = 5 * 60 * 1000; // 5m while waiting around daily start for Day N play to resume
const STUMPS_RESUME_LEAD_MS = 10 * 60 * 1000; // resume ~10m before next day's first ball
const STUMPS_RESUME_WINDOW_AFTER_MS = 45 * 60 * 1000; // treat up to +45m past daily start as today's resume window
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_FIXTURE_AGE_MS = 14 * DAY_MS;
const SLUG_TTL_SEC = 86400;

const REDIS_PREFIX = 'sports:notif:';
const DUE_ZSET_KEY = `${REDIS_PREFIX}due`;
const UPCOMING_HASH_KEY = `${REDIS_PREFIX}upcoming`;
const LIVE_HASH_KEY = `${REDIS_PREFIX}live`;
const SLUG_CACHE_PREFIX = 'sports:slug:';
const REDIS_STATE_TTL_SEC = 48 * 3600;

export interface TrackedUpcomingMatch {
  id: string;
  slug: string;
  title: string;
  team1Id: string;
  team1Short: string;
  team1Name: string;
  team2Id: string;
  team2Short: string;
  team2Name: string;
  venue: string;
  startTimestamp: number;
  status: string;
  startWatchUntil: number;
}

export interface TrackedLiveMatch {
  id: string;
  slug: string;
  title: string;
  team1Id: string;
  team1Short: string;
  team1Name: string;
  team2Id: string;
  team2Short: string;
  team2Name: string;
  startTimestamp: number | null;
  startedNotified: boolean;
  lastStatus: string;
  lastUpdateKind: 'toss' | 'stumps' | 'break' | 'delay' | null;
  delayEpisode: number;
  isStumps: boolean;
  stumpsResumeAt: number | null;
  currentInnings: number;
  hasInningsBaseline: boolean;
  lastBatters: Record<string, number>;
}

const MATCH_UPDATE_PATTERNS: Array<{
  kind: 'toss' | 'stumps' | 'break' | 'delay';
  regex: RegExp;
}> = [
  { kind: 'toss', regex: /\b(opt to bat|opt to bowl|elected to bat|elected to field|won the toss)\b/i },
  { kind: 'stumps', regex: /\bstumps\b/i },
  { kind: 'break', regex: /\b(innings break|lunch|tea|drinks)\b/i },
  { kind: 'delay', regex: /\b(rain|delayed|wet outfield|inspection|bad light)\b/i },
];

export function classifyMatchUpdateKind(status: string): 'toss' | 'stumps' | 'break' | 'delay' | null {
  const s = status.trim();
  if (!s) return null;
  for (const p of MATCH_UPDATE_PATTERNS) {
    if (p.regex.test(s)) return p.kind;
  }
  return null;
}

export function countStartedInnings(team1Score: string, team2Score: string): number {
  const countParts = (raw: string): number => {
    const s = (raw || '').trim();
    if (!s || /^yet to bat$/i.test(s) || /^scheduled$/i.test(s)) return 0;
    return s.split(' & ').filter((part) => part.trim().length > 0).length;
  };
  return Math.max(1, countParts(team1Score) + countParts(team2Score));
}

/**
 * Semantic dedup suffix (never raw payload text hashes) so wording tweaks do not duplicate pushes.
 */
export function buildSemanticUpdateKey(
  matchId: string,
  kind: 'toss' | 'stumps' | 'break' | 'delay',
  status: string,
  now: number,
  inningsCount: number = 1,
  delayEpisode: number = 1,
): string {
  const utcDay = new Date(now).toISOString().slice(0, 10);
  switch (kind) {
    case 'toss':
      return `${matchId}:upd:toss`;
    case 'stumps': {
      const dayMatch = status.match(/\bday\s*(\d+)\b/i);
      const dayTag = dayMatch ? `day${dayMatch[1]}` : utcDay;
      return `${matchId}:upd:stumps:${dayTag}`;
    }
    case 'break': {
      const lower = status.toLowerCase();
      const subKind = lower.includes('lunch')
        ? 'lunch'
        : lower.includes('tea')
          ? 'tea'
          : lower.includes('drinks')
            ? 'drinks'
            : 'innings';
      const dayMatch = status.match(/\bday\s*(\d+)\b/i);
      const bucket = subKind === 'innings' ? `inn${inningsCount}` : dayMatch ? `day${dayMatch[1]}` : utcDay;
      return `${matchId}:upd:break:${subKind}:${bucket}`;
    }
    case 'delay':
      return `${matchId}:upd:delay:ep${delayEpisode}`;
  }
}

/**
 * "Started" means the first ball has been bowled (score with runs/wickets > 0 or overs > 0).
 * Inspects structured team score strings ONLY (never free-text status like "reduced to 18 ov")
 * and excludes Cricbuzz pre-play strings such as "Yet to bat", "0/0", or "0-0 (0.0)".
 */
export function hasFirstBallBeenBowled(match: {
  team1Score: string;
  team2Score: string;
  status: string;
}): boolean {
  for (const raw of [match.team1Score, match.team2Score]) {
    const s = (raw || '').trim();
    if (!s || /^yet to bat$/i.test(s) || /^scheduled$/i.test(s)) continue;

    // Check parenthesized overs emitted by cricbuzzMapper, e.g. "(0.1)", "(12.4)", "(0.1 ov)"
    const ovMatches = s.matchAll(/\((\d+(?:\.\d+)?)(?:\s*ov)?\)/gi);
    for (const m of ovMatches) {
      if (parseFloat(m[1]) > 0) return true;
    }

    // Check explicit score e.g. "4/0", "12-1" while ignoring "0/0" / "0-0"
    const scoreMatches = s.matchAll(/\b(\d+)\s*[\/\-]\s*(\d+)/g);
    for (const m of scoreMatches) {
      if (Number(m[1]) > 0 || Number(m[2]) > 0) return true;
    }
  }

  return false;
}

/**
 * Compute next day's first-ball resume timestamp minus 10 minutes in O(1).
 * If already inside today's resume window [dailyFirstBall - 10m, dailyFirstBall + 45m],
 * polls every 5 minutes until Day N play starts instead of skipping a full 24h to Day N+1.
 * Falls back to now + 30 min if the start time is missing or stale (> 14 days old).
 */
export function computeStumpsResumeAt(startTimestamp: number | null, now: number): number {
  if (!startTimestamp || !Number.isFinite(startTimestamp) || Math.abs(now - startTimestamp) > MAX_FIXTURE_AGE_MS) {
    return now + STUMPS_FALLBACK_POLL_MS;
  }

  // Find the nearest daily start >= startTimestamp around `now`
  const elapsed = Math.max(0, now - startTimestamp);
  const baseDayIndex = Math.floor(elapsed / DAY_MS);

  for (const offset of [0, 1]) {
    const candidateStart = startTimestamp + (baseDayIndex + offset) * DAY_MS;
    const windowOpen = candidateStart - STUMPS_RESUME_LEAD_MS;
    const windowClose = candidateStart + STUMPS_RESUME_WINDOW_AFTER_MS;

    if (now >= windowOpen && now <= windowClose) {
      return now + STUMPS_MORNING_POLL_MS;
    }
    if (now < windowOpen) {
      return windowOpen;
    }
  }

  return startTimestamp + (baseDayIndex + 2) * DAY_MS - STUMPS_RESUME_LEAD_MS;
}

export function isBatterInMilestoneZone(batsmen: BatsmanLive[]): boolean {
  return batsmen.some((b) => (b.runs >= 35 && b.runs < 50) || (b.runs >= 85 && b.runs < 100));
}

export function detectBatterMilestones(
  prevRunsByName: Record<string, number>,
  nextBatsmen: BatsmanLive[],
  hasInningsBaseline: boolean = false,
): Array<{ name: string; milestone: 50 | 100; runs: number; balls: number }> {
  const milestones: Array<{ name: string; milestone: 50 | 100; runs: number; balls: number }> = [];
  for (const bat of nextBatsmen) {
    const norm = bat.name.trim().toLowerCase();
    const recordedPrev = prevRunsByName[norm];
    // On first snapshot of an innings, treat unknown batters as pre-existing so mid-match attaches don't re-alert;
    // once baseline is established, a brand-new batter starts from 0 even if he rockets to 50 within one poll.
    const prevRuns = recordedPrev !== undefined ? recordedPrev : hasInningsBaseline ? 0 : undefined;
    if (prevRuns === undefined) continue;
    // Check 100 before 50: if a single slow poll jumps past both 50 and 100, emit ONLY 100
    if (prevRuns < 100 && bat.runs >= 100) {
      milestones.push({ name: bat.name, milestone: 100, runs: bat.runs, balls: bat.balls });
    } else if (prevRuns < 50 && bat.runs >= 50) {
      milestones.push({ name: bat.name, milestone: 50, runs: bat.runs, balls: bat.balls });
    }
  }
  return milestones;
}

export function subscriberMatchesMatch(
  sub: ActiveSubscriber,
  match: {
    team1Id: string;
    team1Short: string;
    team1Name: string;
    team2Id: string;
    team2Short: string;
    team2Name: string;
  },
): boolean {
  const check = (id: string, short: string, name: string): boolean =>
    Boolean(
      (id && sub.followedTeamIds.has(id.trim().toLowerCase())) ||
        (short && sub.followedShortNames.has(short.trim().toLowerCase())) ||
        (name && sub.followedTeamNames.has(name.trim().toLowerCase())),
    );

  return (
    check(match.team1Id, match.team1Short, match.team1Name) ||
    check(match.team2Id, match.team2Short, match.team2Name)
  );
}

type MatchInterest = {
  wantsStartingSoon: boolean;
  wantsStarted: boolean;
  wantsMilestones: boolean;
  wantsUpdatesOrResult: boolean;
  anyUpcoming: boolean;
  anyLive: (startedNotified: boolean) => boolean;
};

function getMatchInterest(
  subscribers: ActiveSubscriber[],
  match: {
    team1Id: string;
    team1Short: string;
    team1Name: string;
    team2Id: string;
    team2Short: string;
    team2Name: string;
  },
): MatchInterest {
  const matching = subscribers.filter((s) => s.preferences.enabled && subscriberMatchesMatch(s, match));
  const wantsStartingSoon = matching.some((s) => s.preferences.matchStartingSoon);
  const wantsStarted = matching.some((s) => s.preferences.matchStarted);
  const wantsMilestones = matching.some((s) => s.preferences.milestones);
  const wantsUpdatesOrResult = matching.some(
    (s) => s.preferences.matchUpdates || s.preferences.matchConcluded,
  );
  return {
    wantsStartingSoon,
    wantsStarted,
    wantsMilestones,
    wantsUpdatesOrResult,
    anyUpcoming: wantsStartingSoon || wantsStarted || wantsMilestones || wantsUpdatesOrResult,
    anyLive: (startedNotified: boolean) =>
      (!startedNotified && wantsStarted) || wantsMilestones || wantsUpdatesOrResult,
  };
}

/**
 * Adaptive event-driven sports notification scheduler backed by a Redis sorted set (`ZADD`/`ZRANGEBYSCORE`).
 *
 * - Never runs when Redis is down (`redis?.status !== 'ready'`).
 * - Each tick issues one cheap `ZRANGEBYSCORE` and performs zero Cricbuzz scrapes unless work is due.
 * - Upcoming & already-live discovery runs once every 6h (and on-demand when a user follows a team / enables alerts).
 * - Start watch (`2.5 min`) runs ONLY from T-5m until first ball bowled (`startedNotified`) or T+90m.
 * - During play:
 *     * If any follower of THAT match has `milestones: true`: ONLY `milestone:<id>` (`getMatchDetails`, 6m / 60s)
 *       runs — handling milestones, updates, and final result from one response (`0` `getMatches('live')` calls).
 *     * If NO follower of THAT match wants milestones, but wants updates/result: `liveLiteWatch` polls
 *       `getMatches('live')` every 10 minutes (paused at Stumps until next day's first ball - 10m).
 *     * If followers disabled all live alerts for THAT match: 0 polling.
 */
export class SportsNotifier {
  private timer: NodeJS.Timeout | null = null;
  private isTicking = false;
  private inFlightMatchIds = new Set<string>();
  private upcomingById = new Map<string, TrackedUpcomingMatch>();
  private liveById = new Map<string, TrackedLiveMatch>();
  private seededSweep = false;

  public start(tickMs: number = 30_000): void {
    if (this.timer) return;
    this.timer = setInterval(() => {
      void this.tick();
    }, tickMs);
    logger.info({ tickMs }, 'Sports notification scheduler started');
  }

  public stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /**
   * Queue an immediate one-shot discovery sweep (e.g. when a user newly follows a team or re-enables alerts).
   */
  public async requestDiscoverySweep(now: number = Date.now()): Promise<void> {
    const redis = this.getReadyRedis();
    if (!redis) return;
    try {
      await this.scheduleDue(redis, 'sweep:discovery', now);
    } catch {
      // Non-fatal
    }
  }

  /**
   * Zero-cost hook called by SportsWorker whenever a viewer is actively watching a match.
   */
  public async onLiveDetailsObserved(details: MatchDetails): Promise<void> {
    if (this.isTicking || this.inFlightMatchIds.has(details.id)) return;
    const redis = this.getReadyRedis();
    if (!redis) return;
    this.inFlightMatchIds.add(details.id);
    try {
      await this.hydrateLiveIfNeeded(redis, details.id);
      const tracked = this.liveById.get(details.id);
      if (!tracked) return;
      const subscribers = await getActiveSportsSubscribers().catch(() => []);
      if (subscribers.length === 0) return;
      await this.processLiveDetailsSnapshot(redis, tracked, details, subscribers, Date.now());
    } finally {
      this.inFlightMatchIds.delete(details.id);
    }
  }

  public async tick(now: number = Date.now()): Promise<void> {
    if (this.isTicking) return;
    const redis = this.getReadyRedis();
    if (!redis) {
      return; // Do not run poller when Redis is unavailable
    }

    this.isTicking = true;
    try {
      if (!this.seededSweep) {
        await redis.zadd(DUE_ZSET_KEY, 'NX', String(now), 'sweep:upcoming');
        this.seededSweep = true;
      }

      const dueMembers = await redis.zrangebyscore(DUE_ZSET_KEY, '-inf', String(now));
      if (dueMembers.length === 0) {
        return; // Zero Cricbuzz calls when nothing is due
      }

      const subscribers = await getActiveSportsSubscribers();
      if (subscribers.length === 0) {
        await this.zremMembers(redis, dueMembers);
        await this.scheduleDue(redis, 'sweep:upcoming', now + SUBSCRIBER_IDLE_RETRY_MS);
        return;
      }

      const startSoonIds: string[] = [];
      const milestoneIds: string[] = [];
      let runDiscoverySweep = false;
      let runStartWatch = false;
      let runLiveLite = false;

      for (const member of dueMembers) {
        if (member === 'sweep:upcoming' || member === 'sweep:discovery') {
          runDiscoverySweep = true;
          if (member === 'sweep:discovery') {
            await this.zremMembers(redis, [member]);
          }
        } else if (member.startsWith('startWatch:')) {
          runStartWatch = true;
          await this.zremMembers(redis, [member]);
        } else if (member === 'liveLiteWatch') {
          runLiveLite = true;
          await this.zremMembers(redis, [member]);
        } else if (member.startsWith('startSoon:')) {
          startSoonIds.push(member.slice('startSoon:'.length));
        } else if (member.startsWith('milestone:')) {
          milestoneIds.push(member.slice('milestone:'.length));
        }
      }

      if (runDiscoverySweep) {
        await this.sweepDiscovery(redis, subscribers, now);
      }

      if (startSoonIds.length > 0) {
        await this.processStartingSoonTimers(redis, startSoonIds, subscribers, now);
      }

      if (runStartWatch || runLiveLite) {
        await this.sweepLiveFeed(redis, subscribers, now);
      }

      if (milestoneIds.length > 0) {
        await this.processMilestoneChecks(redis, milestoneIds, subscribers, now);
      }
    } catch (error) {
      logger.error({ error }, 'SportsNotifier tick error');
    } finally {
      this.isTicking = false;
    }
  }

  /**
   * Runs every 6h (or on-demand discovery): fetches upcoming AND once-off live feed to track upcoming fixtures
   * and discover already-live / Test Day 2-5 matches.
   */
  private async sweepDiscovery(
    redis: Redis,
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    await this.hydrateAllFromRedis(redis);

    // Prune stale upcoming entries whose start watch window already expired
    for (const [id, up] of Array.from(this.upcomingById.entries())) {
      if (now > up.startWatchUntil) {
        await this.removeUpcoming(redis, id);
      }
    }

    let upcomingRes;
    try {
      upcomingRes = await getMatches('upcoming');
    } catch (error) {
      logger.warn({ error }, 'Upcoming discovery sweep failed — retrying in 15 min');
      await this.scheduleDue(redis, 'sweep:upcoming', now + DISCOVERY_ERROR_RETRY_MS);
      return;
    }

    const liveRes = await getMatches('live').catch(() => ({ matches: [] as MatchSummary[] }));
    await this.scheduleDue(redis, 'sweep:upcoming', now + UPCOMING_SWEEP_MS);

    const upcomingMatches = upcomingRes.matches;
    const withTs = upcomingMatches.filter((m) => m.startTimestamp != null);
    if (withTs.length > 0) {
      const maxStart = Math.max(...withTs.map((m) => m.startTimestamp!));
      logger.info(
        {
          upcomingCount: upcomingMatches.length,
          maxHorizonHours: Number(((maxStart - now) / 36e5).toFixed(2)),
        },
        'Upcoming fixture sweep completed',
      );
    }

    for (const m of upcomingMatches) {
      if (m.state !== 'upcoming' || !m.startTimestamp) continue;
      if (this.liveById.has(m.id) || (await redis.hexists(LIVE_HASH_KEY, m.id))) continue;
      const descriptor = this.toDescriptor(m);
      const interest = getMatchInterest(subscribers, descriptor);
      if (!interest.anyUpcoming) continue;

      const tracked: TrackedUpcomingMatch = {
        ...descriptor,
        venue: m.venue,
        startTimestamp: m.startTimestamp,
        status: m.status,
        startWatchUntil: m.startTimestamp + START_WATCH_CUTOFF_MS,
      };
      this.upcomingById.set(m.id, tracked);
      await redis.hset(UPCOMING_HASH_KEY, m.id, JSON.stringify(tracked));

      if (interest.wantsStartingSoon && now < m.startTimestamp - START_SOON_EXPIRY_CUTOFF_MS) {
        await this.scheduleDue(
          redis,
          `startSoon:${m.id}`,
          Math.max(now, m.startTimestamp - START_SOON_LEAD_MS),
        );
      }
      if (interest.anyLive(false) && now <= tracked.startWatchUntil) {
        await this.scheduleDue(
          redis,
          `startWatch:${m.id}`,
          Math.max(now, m.startTimestamp - START_WATCH_LEAD_MS),
        );
      }
    }

    // One-shot discovery of any already-live match (e.g. Day 2-5 Test or followed mid-match)
    for (const m of liveRes.matches) {
      const desc = this.toDescriptor(m);
      const interest = getMatchInterest(subscribers, desc);
      const alreadyBowled = hasFirstBallBeenBowled(m);
      if (!interest.anyLive(alreadyBowled)) continue;
      if (!this.liveById.has(m.id)) {
        await this.promoteToLiveTracked(redis, m, m.startTimestamp, interest, now);
      }
    }
  }

  private async processStartingSoonTimers(
    redis: Redis,
    matchIds: string[],
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    const dueMatches: TrackedUpcomingMatch[] = [];
    for (const id of matchIds) {
      await this.zremMembers(redis, [`startSoon:${id}`]);
      const tracked = await this.getUpcomingTracked(redis, id);
      if (!tracked) continue;
      if (now >= tracked.startTimestamp - START_SOON_EXPIRY_CUTOFF_MS) {
        continue;
      }
      dueMatches.push(tracked);
    }
    if (dueMatches.length === 0) return;

    const latestUpcoming = await getMatches('upcoming').catch(() => null);
    const byId = new Map((latestUpcoming?.matches || []).map((m) => [m.id, m]));

    for (const tracked of dueMatches) {
      const fresh = byId.get(tracked.id);
      if (fresh) {
        if (fresh.state === 'abandoned' || fresh.state === 'no_result') {
          await this.removeUpcoming(redis, tracked.id);
          continue;
        }
        if (fresh.state !== 'upcoming') {
          continue;
        }
        if (
          fresh.startTimestamp &&
          fresh.startTimestamp - now > START_SOON_LEAD_MS + 5 * 60 * 1000
        ) {
          tracked.startTimestamp = fresh.startTimestamp;
          tracked.startWatchUntil = fresh.startTimestamp + START_WATCH_CUTOFF_MS;
          this.upcomingById.set(tracked.id, tracked);
          await redis.hset(UPCOMING_HASH_KEY, tracked.id, JSON.stringify(tracked));
          await this.scheduleDue(
            redis,
            `startSoon:${tracked.id}`,
            fresh.startTimestamp - START_SOON_LEAD_MS,
          );
          await this.scheduleDue(
            redis,
            `startWatch:${tracked.id}`,
            fresh.startTimestamp - START_WATCH_LEAD_MS,
          );
          continue;
        }
        tracked.status = fresh.status;
      }

      if (now >= tracked.startTimestamp - START_SOON_EXPIRY_CUTOFF_MS) {
        continue;
      }

      if (await this.claimDedup(redis, `${tracked.id}:startSoon`)) {
        const subTitle = `${tracked.team1Short} vs ${tracked.team2Short}`;
        const body = tracked.venue
          ? `Starts in ~30 mins • ${tracked.venue}`
          : 'Starts in ~30 mins';
        await this.dispatchToMatchingSubscribers(subscribers, tracked, 'matchStartingSoon', {
          title: `Starting Soon: ${subTitle}`,
          body,
          matchId: tracked.id,
          eventType: 'match_starting_soon',
          channelId: 'match_lifecycle',
        });
      }

      await this.maybeDispatchStatusUpdate(redis, subscribers, tracked, tracked.status, now, 1, 1);
    }
  }

  private async sweepLiveFeed(
    redis: Redis,
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    await this.hydrateAllFromRedis(redis);

    const { matches: liveMatches } = await getMatches('live');
    const liveByIdNow = new Map(liveMatches.map((m) => [m.id, m]));

    // 1. Check upcoming matches in startWatch window
    for (const [id, up] of Array.from(this.upcomingById.entries())) {
      const interest = getMatchInterest(subscribers, up);
      if (!interest.anyUpcoming) {
        await this.removeUpcoming(redis, id);
        continue;
      }

      const liveMatch = liveByIdNow.get(id);
      if (liveMatch) {
        await this.removeUpcoming(redis, id);
        if (!this.liveById.has(id) && interest.anyLive(hasFirstBallBeenBowled(liveMatch))) {
          await this.promoteToLiveTracked(redis, liveMatch, up.startTimestamp, interest, now);
        }
      } else if (now > up.startWatchUntil) {
        await this.removeUpcoming(redis, id);
      } else if (interest.anyLive(false) && now >= up.startTimestamp - START_WATCH_LEAD_MS) {
        await this.scheduleDue(redis, `startWatch:${id}`, now + START_WATCH_POLL_MS);
      }
    }

    // 2. Process tracked live matches that need live-list checks (unstarted OR non-milestone lite mode)
    let nextLiveLiteDue: number | null = null;
    for (const [id, tracked] of Array.from(this.liveById.entries())) {
      const interest = getMatchInterest(subscribers, tracked);
      if (!interest.anyLive(tracked.startedNotified)) {
        await this.removeLive(redis, id);
        continue;
      }

      if (tracked.startedNotified && interest.wantsMilestones) {
        continue;
      }

      const current = liveByIdNow.get(id);
      if (!current) {
        const retried = await this.handleDisappearedFromLive(redis, tracked, subscribers, now);
        if (retried) {
          nextLiveLiteDue = Math.min(nextLiveLiteDue ?? Infinity, now + CONCLUDED_RETRY_MS);
        } else {
          await this.removeLive(redis, id);
        }
        continue;
      }

      if (!tracked.startedNotified) {
        await this.checkAndHandleFirstBall(redis, tracked, current, interest, subscribers, now);
      }

      if (current.status && current.status !== tracked.lastStatus) {
        const wasStumps = tracked.isStumps;
        tracked.lastStatus = current.status;
        const kind = classifyMatchUpdateKind(current.status);
        tracked.isStumps = kind === 'stumps';

        if (tracked.isStumps) {
          tracked.stumpsResumeAt = computeStumpsResumeAt(tracked.startTimestamp, now);
        } else if (wasStumps) {
          tracked.stumpsResumeAt = null;
        }
        const inningsCount = countStartedInnings(current.team1Score, current.team2Score);
        tracked.currentInnings = inningsCount;
        await this.dispatchFilteredStatusUpdate(
          redis,
          tracked,
          subscribers,
          current.status,
          now,
          inningsCount,
        );
      }

      if (!tracked.startedNotified) {
        const withinWindow = !tracked.startTimestamp || now <= tracked.startTimestamp + START_WATCH_CUTOFF_MS;
        if (withinWindow) {
          await this.scheduleDue(redis, `startWatch:${id}`, now + START_WATCH_POLL_MS);
          continue;
        }
      }

      if (!interest.wantsMilestones && interest.wantsUpdatesOrResult) {
        const due = tracked.isStumps
          ? tracked.stumpsResumeAt ?? now + STUMPS_FALLBACK_POLL_MS
          : now + LIVE_LITE_POLL_MS;
        nextLiveLiteDue = Math.min(nextLiveLiteDue ?? Infinity, due);
      }
    }

    if (nextLiveLiteDue !== null) {
      await this.scheduleDue(redis, 'liveLiteWatch', nextLiveLiteDue);
    }
  }

  private async processMilestoneChecks(
    redis: Redis,
    matchIds: string[],
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    for (const id of matchIds) {
      await this.zremMembers(redis, [`milestone:${id}`]);
      const tracked = await this.hydrateLiveIfNeeded(redis, id);
      if (!tracked) continue;

      const interest = getMatchInterest(subscribers, tracked);
      if (!interest.wantsMilestones) {
        if (interest.wantsUpdatesOrResult) {
          await this.scheduleDue(
            redis,
            'liveLiteWatch',
            tracked.isStumps ? tracked.stumpsResumeAt ?? now + STUMPS_FALLBACK_POLL_MS : now + LIVE_LITE_POLL_MS,
          );
        } else if (!tracked.startedNotified && interest.wantsStarted) {
          await this.scheduleDue(redis, `startWatch:${id}`, now + START_WATCH_POLL_MS);
        } else {
          await this.removeLive(redis, id);
        }
        continue;
      }

      if (tracked.isStumps && tracked.stumpsResumeAt && now < tracked.stumpsResumeAt) {
        await this.scheduleDue(redis, `milestone:${id}`, tracked.stumpsResumeAt);
        continue;
      }

      await this.ensureSlugMapping(redis, id, tracked.slug);
      const detailsRes = await getMatchDetails(id, true).catch(() => null);
      if (detailsRes?.match) {
        await this.processLiveDetailsSnapshot(redis, tracked, detailsRes.match, subscribers, now);
      } else {
        await this.scheduleDue(redis, `milestone:${id}`, now + MILESTONE_BASE_POLL_MS);
      }
    }
  }

  private async processLiveDetailsSnapshot(
    redis: Redis,
    tracked: TrackedLiveMatch,
    details: MatchDetails,
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    const interest = getMatchInterest(subscribers, tracked);
    if (!interest.anyLive(tracked.startedNotified)) {
      await this.removeLive(redis, tracked.id);
      return;
    }

    if (!details.isLive || details.state !== 'live') {
      if (details.state !== 'upcoming') {
        const retried = await this.dispatchConcludedNotification(redis, tracked, details, subscribers, now);
        if (retried) {
          await this.scheduleDue(redis, `milestone:${tracked.id}`, now + CONCLUDED_RETRY_MS);
          return;
        }
        await this.removeLive(redis, tracked.id);
      }
      return;
    }

    const inningsCount = countStartedInnings(details.team1Score, details.team2Score);
    if (tracked.currentInnings !== inningsCount) {
      // New innings started: reset batter map & baseline so players batting in Innings 2+ can trigger milestones again
      tracked.currentInnings = inningsCount;
      tracked.hasInningsBaseline = false;
      tracked.lastBatters = {};
    }

    if (!tracked.startedNotified) {
      await this.checkAndHandleFirstBall(redis, tracked, details, interest, subscribers, now);
    }

    if (details.status && details.status !== tracked.lastStatus) {
      tracked.lastStatus = details.status;
      await this.dispatchFilteredStatusUpdate(
        redis,
        tracked,
        subscribers,
        details.status,
        now,
        inningsCount,
      );
    }

    const kind = classifyMatchUpdateKind(details.status);
    if (kind === 'stumps') {
      tracked.isStumps = true;
      const resumeAt = computeStumpsResumeAt(tracked.startTimestamp, now);
      tracked.stumpsResumeAt = resumeAt;
      await this.saveLiveTracked(redis, tracked);
      await this.scheduleDue(redis, `milestone:${tracked.id}`, resumeAt);
      return;
    }

    if (tracked.isStumps) {
      tracked.isStumps = false;
      tracked.stumpsResumeAt = null;
    }

    if (!interest.wantsMilestones) {
      await this.saveLiveTracked(redis, tracked);
      return;
    }

    const milestonesHit = detectBatterMilestones(
      tracked.lastBatters,
      details.currentBatsmen,
      tracked.hasInningsBaseline,
    );
    for (const hit of milestonesHit) {
      const norm = hit.name.trim().toLowerCase();
      if (hit.milestone === 100) {
        await this.claimDedup(redis, `${tracked.id}:inn${inningsCount}:m50:${norm}`);
      }
      if (await this.claimDedup(redis, `${tracked.id}:inn${inningsCount}:m${hit.milestone}:${norm}`)) {
        const label = hit.milestone === 100 ? 'CENTURY' : 'FIFTY';
        await this.dispatchToMatchingSubscribers(subscribers, tracked, 'milestones', {
          title: `${label}! ${hit.name} ${hit.runs}(${hit.balls})`,
          body: `${tracked.team1Short} vs ${tracked.team2Short} • ${details.liveScore?.score || details.status}`,
          matchId: tracked.id,
          eventType: 'milestone',
          channelId: 'match_events',
        });
      }
    }

    const updatedBatters: Record<string, number> = { ...tracked.lastBatters };
    for (const bat of details.currentBatsmen) {
      const norm = bat.name.trim().toLowerCase();
      updatedBatters[norm] = Math.max(updatedBatters[norm] || 0, bat.runs);
    }
    tracked.lastBatters = updatedBatters;
    tracked.hasInningsBaseline = true;
    await this.saveLiveTracked(redis, tracked);

    const inHotZone = isBatterInMilestoneZone(details.currentBatsmen);
    await this.scheduleDue(
      redis,
      `milestone:${tracked.id}`,
      now + (inHotZone ? MILESTONE_HOT_POLL_MS : MILESTONE_BASE_POLL_MS),
    );
  }

  private async promoteToLiveTracked(
    redis: Redis,
    m: MatchSummary,
    startTimestamp: number | null,
    interest: MatchInterest,
    now: number,
  ): Promise<TrackedLiveMatch> {
    const kind = classifyMatchUpdateKind(m.status);
    const isStumps = kind === 'stumps';
    const effectiveStart = m.startTimestamp ?? startTimestamp;
    const stumpsResumeAt = isStumps ? computeStumpsResumeAt(effectiveStart, now) : null;
    const alreadyBowled = hasFirstBallBeenBowled(m);
    const inningsCount = countStartedInnings(m.team1Score, m.team2Score);

    const tracked: TrackedLiveMatch = {
      ...this.toDescriptor(m),
      startTimestamp: effectiveStart,
      startedNotified: alreadyBowled,
      lastStatus: m.status,
      lastUpdateKind: kind,
      delayEpisode: kind === 'delay' ? 1 : 0,
      isStumps,
      stumpsResumeAt,
      currentInnings: inningsCount,
      hasInningsBaseline: false,
      lastBatters: {},
    };
    this.liveById.set(m.id, tracked);
    await this.saveLiveTracked(redis, tracked);

    if (!alreadyBowled) {
      if (!effectiveStart || now <= effectiveStart + START_WATCH_CUTOFF_MS) {
        await this.scheduleDue(redis, `startWatch:${m.id}`, now + START_WATCH_POLL_MS);
      }
    } else if (interest.wantsMilestones) {
      await this.scheduleDue(
        redis,
        `milestone:${m.id}`,
        stumpsResumeAt ?? now + MILESTONE_BASE_POLL_MS,
      );
    } else if (interest.wantsUpdatesOrResult) {
      await this.scheduleDue(
        redis,
        'liveLiteWatch',
        stumpsResumeAt ?? now + LIVE_LITE_POLL_MS,
      );
    }

    return tracked;
  }

  private async checkAndHandleFirstBall(
    redis: Redis,
    tracked: TrackedLiveMatch,
    match: { team1Score: string; team2Score: string; status: string },
    interest: MatchInterest,
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<void> {
    if (tracked.startedNotified || !hasFirstBallBeenBowled(match)) return;
    tracked.startedNotified = true;
    await this.zremMembers(redis, [`startWatch:${tracked.id}`]);

    const startExpiry = tracked.startTimestamp ? tracked.startTimestamp + START_WATCH_CUTOFF_MS : null;
    if (!startExpiry || now <= startExpiry) {
      if (await this.claimDedup(redis, `${tracked.id}:started`)) {
        await this.dispatchToMatchingSubscribers(subscribers, tracked, 'matchStarted', {
          title: `Match Started: ${tracked.team1Short} vs ${tracked.team2Short}`,
          body: match.status || 'Play has begun',
          matchId: tracked.id,
          eventType: 'match_started',
          channelId: 'match_lifecycle',
        });
      }
    }

    await this.saveLiveTracked(redis, tracked);
    if (interest.wantsMilestones) {
      await this.scheduleDue(redis, `milestone:${tracked.id}`, now + MILESTONE_BASE_POLL_MS);
    } else if (interest.wantsUpdatesOrResult) {
      await this.scheduleDue(redis, 'liveLiteWatch', now + LIVE_LITE_POLL_MS);
    }
  }

  private async dispatchFilteredStatusUpdate(
    redis: Redis,
    tracked: TrackedLiveMatch,
    subscribers: ActiveSubscriber[],
    status: string,
    now: number,
    inningsCount: number,
  ): Promise<void> {
    const kind = classifyMatchUpdateKind(status);
    // Avoid re-alerting continuously for a single unbroken rain delay across hour boundaries
    if (kind === 'delay' && tracked.lastUpdateKind === 'delay') {
      await this.saveLiveTracked(redis, tracked);
      return;
    }
    if (kind === 'delay') {
      tracked.delayEpisode = (tracked.delayEpisode || 0) + 1;
    }
    tracked.lastUpdateKind = kind;
    await this.saveLiveTracked(redis, tracked);
    if (kind) {
      await this.maybeDispatchStatusUpdate(
        redis,
        subscribers,
        tracked,
        status,
        now,
        inningsCount,
        tracked.delayEpisode || 1,
      );
    }
  }

  private async handleDisappearedFromLive(
    redis: Redis,
    tracked: TrackedLiveMatch,
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<boolean> {
    await this.ensureSlugMapping(redis, tracked.id, tracked.slug);
    const detailsRes = await getMatchDetails(tracked.id, true).catch(() => null);
    const finalSummary = detailsRes?.match;
    if (finalSummary && finalSummary.state !== 'live') {
      return this.dispatchConcludedNotification(redis, tracked, finalSummary, subscribers, now);
    }
    return false;
  }

  private async dispatchConcludedNotification(
    redis: Redis,
    tracked: TrackedLiveMatch,
    finalSummary: { status: string; endTimestamp: number | null },
    subscribers: ActiveSubscriber[],
    now: number,
  ): Promise<boolean> {
    const isExpired =
      finalSummary.endTimestamp != null && now - finalSummary.endTimestamp > CONCLUDED_EXPIRY_MS;
    if (!isExpired && (await this.claimDedup(redis, `${tracked.id}:concluded`))) {
      const sendRes = await this.dispatchToMatchingSubscribers(
        subscribers,
        tracked,
        'matchConcluded',
        {
          title: `Match Result: ${tracked.team1Short} vs ${tracked.team2Short}`,
          body: finalSummary.status || 'Match concluded',
          matchId: tracked.id,
          eventType: 'match_concluded',
          channelId: 'match_lifecycle',
        },
      );
      if (sendRes.transportFailed) {
        await this.releaseDedup(redis, `${tracked.id}:concluded`);
        return true; // signal caller to retry
      }
    }
    return false;
  }

  private async maybeDispatchStatusUpdate(
    redis: Redis,
    subscribers: ActiveSubscriber[],
    match: {
      id: string;
      team1Id: string;
      team1Short: string;
      team1Name: string;
      team2Id: string;
      team2Short: string;
      team2Name: string;
    },
    status: string,
    now: number,
    inningsCount: number,
    delayEpisode: number,
  ): Promise<void> {
    const kind = classifyMatchUpdateKind(status);
    if (!kind) return;
    const semanticKey = buildSemanticUpdateKey(
      match.id,
      kind,
      status,
      now,
      inningsCount,
      delayEpisode,
    );
    if (!(await this.claimDedup(redis, semanticKey))) return;

    await this.dispatchToMatchingSubscribers(subscribers, match, 'matchUpdates', {
      title: `${match.team1Short} vs ${match.team2Short}`,
      body: status,
      matchId: match.id,
      eventType: 'match_update',
      channelId: 'match_lifecycle',
    });
  }

  private async dispatchToMatchingSubscribers(
    subscribers: ActiveSubscriber[],
    match: {
      team1Id: string;
      team1Short: string;
      team1Name: string;
      team2Id: string;
      team2Short: string;
      team2Name: string;
    },
    prefKey: keyof ISportsNotificationPrefs,
    payload: SportsPushMessage,
  ): Promise<{ sent: number; failed: number; transportFailed: boolean }> {
    const tokens = subscribers
      .filter((s) => s.preferences.enabled && s.preferences[prefKey] && subscriberMatchesMatch(s, match))
      .flatMap((s) => s.tokens);
    if (tokens.length === 0) return { sent: 0, failed: 0, transportFailed: false };
    return sendPushToTokens(tokens, payload);
  }

  private async ensureSlugMapping(redis: Redis, matchId: string, slug: string): Promise<void> {
    if (!slug) return;
    try {
      await redis.set(`${SLUG_CACHE_PREFIX}${matchId}`, slug, 'EX', SLUG_TTL_SEC, 'NX');
    } catch {
      // Non-fatal
    }
  }

  private async getUpcomingTracked(redis: Redis, id: string): Promise<TrackedUpcomingMatch | null> {
    const cached = this.upcomingById.get(id);
    if (cached) return cached;
    const raw = await redis.hget(UPCOMING_HASH_KEY, id);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as TrackedUpcomingMatch;
      this.upcomingById.set(id, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  private async hydrateLiveIfNeeded(redis: Redis, id: string): Promise<TrackedLiveMatch | null> {
    const cached = this.liveById.get(id);
    if (cached) return cached;
    const raw = await redis.hget(LIVE_HASH_KEY, id);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as TrackedLiveMatch;
      this.liveById.set(id, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  private async hydrateAllFromRedis(redis: Redis): Promise<void> {
    const [upMap, liveMap] = await Promise.all([
      redis.hgetall(UPCOMING_HASH_KEY),
      redis.hgetall(LIVE_HASH_KEY),
    ]);
    for (const [id, raw] of Object.entries(upMap)) {
      if (!this.upcomingById.has(id)) {
        try {
          this.upcomingById.set(id, JSON.parse(raw) as TrackedUpcomingMatch);
        } catch {
          // ignore malformed entry
        }
      }
    }
    for (const [id, raw] of Object.entries(liveMap)) {
      if (!this.liveById.has(id)) {
        try {
          this.liveById.set(id, JSON.parse(raw) as TrackedLiveMatch);
        } catch {
          // ignore malformed entry
        }
      }
    }
  }

  private async saveLiveTracked(redis: Redis, tracked: TrackedLiveMatch): Promise<void> {
    await redis.hset(LIVE_HASH_KEY, tracked.id, JSON.stringify(tracked));
  }

  private async removeUpcoming(redis: Redis, id: string): Promise<void> {
    this.upcomingById.delete(id);
    await redis.hdel(UPCOMING_HASH_KEY, id);
    await this.zremMembers(redis, [`startSoon:${id}`, `startWatch:${id}`]);
  }

  private async removeLive(redis: Redis, id: string): Promise<void> {
    this.liveById.delete(id);
    await redis.hdel(LIVE_HASH_KEY, id);
    await this.zremMembers(redis, [`milestone:${id}`, `startWatch:${id}`]);
  }

  private async scheduleDue(redis: Redis, member: string, timestampMs: number): Promise<void> {
    await redis.zadd(DUE_ZSET_KEY, String(Math.floor(timestampMs)), member);
  }

  private async zremMembers(redis: Redis, members: string[]): Promise<void> {
    if (members.length === 0) return;
    await redis.zrem(DUE_ZSET_KEY, ...members);
  }

  private getReadyRedis(): Redis | null {
    try {
      const redis = getRedisClient();
      if (!redis || redis.status !== 'ready') return null;
      return redis;
    } catch {
      return null;
    }
  }

  private toDescriptor(m: MatchSummary) {
    return {
      id: m.id,
      slug: m.slug,
      title: `${m.team1.shortName} vs ${m.team2.shortName}`,
      team1Id: m.team1.id,
      team1Short: m.team1.shortName,
      team1Name: m.team1.name,
      team2Id: m.team2.id,
      team2Short: m.team2.shortName,
      team2Name: m.team2.name,
    };
  }

  private async claimDedup(redis: Redis, key: string): Promise<boolean> {
    const res = await redis.set(`${REDIS_PREFIX}sent:${key}`, '1', 'EX', REDIS_STATE_TTL_SEC, 'NX');
    return res === 'OK';
  }

  private async releaseDedup(redis: Redis, key: string): Promise<void> {
    await redis.del(`${REDIS_PREFIX}sent:${key}`);
  }
}

export const sportsNotifier = new SportsNotifier();
