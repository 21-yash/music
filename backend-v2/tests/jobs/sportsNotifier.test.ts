import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  SportsNotifier,
  classifyMatchUpdateKind,
  buildSemanticUpdateKey,
  hasFirstBallBeenBowled,
  computeStumpsResumeAt,
  isBatterInMilestoneZone,
  detectBatterMilestones,
  subscriberMatchesMatch,
} from '../../src/jobs/sportsNotifier';
import type { ActiveSubscriber } from '../../src/services/notificationService';
import type { BatsmanLive, MatchSummary, MatchDetails } from '../../src/providers/sports/types';

class InMemoryRedis {
  public status = 'ready';
  private zsets = new Map<string, Map<string, number>>();
  private hashes = new Map<string, Map<string, string>>();
  private kv = new Map<string, string>();

  async zadd(key: string, ...args: string[]): Promise<number> {
    if (!this.zsets.has(key)) this.zsets.set(key, new Map());
    const map = this.zsets.get(key)!;
    let idx = 0;
    const isNx = args[0]?.toUpperCase() === 'NX';
    if (isNx) idx = 1;
    const score = Number(args[idx]);
    const member = args[idx + 1];
    if (isNx && map.has(member)) return 0;
    map.set(member, score);
    return 1;
  }

  async zrangebyscore(key: string, min: string, max: string): Promise<string[]> {
    const map = this.zsets.get(key);
    if (!map) return [];
    const minNum = min === '-inf' ? -Infinity : Number(min);
    const maxNum = max === '+inf' ? Infinity : Number(max);
    return Array.from(map.entries())
      .filter(([, s]) => s >= minNum && s <= maxNum)
      .sort((a, b) => a[1] - b[1])
      .map(([m]) => m);
  }

  async zrem(key: string, ...members: string[]): Promise<number> {
    const map = this.zsets.get(key);
    if (!map) return 0;
    let count = 0;
    for (const m of members) {
      if (map.delete(m)) count++;
    }
    return count;
  }

  async zscore(key: string, member: string): Promise<string | null> {
    const score = this.zsets.get(key)?.get(member);
    return score !== undefined ? String(score) : null;
  }

  async hset(key: string, field: string, value: string): Promise<number> {
    if (!this.hashes.has(key)) this.hashes.set(key, new Map());
    this.hashes.get(key)!.set(field, value);
    return 1;
  }

  async hget(key: string, field: string): Promise<string | null> {
    return this.hashes.get(key)?.get(field) ?? null;
  }

  async hexists(key: string, field: string): Promise<number> {
    return this.hashes.get(key)?.has(field) ? 1 : 0;
  }

  async hgetall(key: string): Promise<Record<string, string>> {
    const map = this.hashes.get(key);
    if (!map) return {};
    return Object.fromEntries(map.entries());
  }

  async hdel(key: string, ...fields: string[]): Promise<number> {
    const map = this.hashes.get(key);
    if (!map) return 0;
    let count = 0;
    for (const f of fields) {
      if (map.delete(f)) count++;
    }
    return count;
  }

  async set(key: string, value: string, ...args: (string | number)[]): Promise<'OK' | null> {
    const hasNx = args.some((a) => String(a).toUpperCase() === 'NX');
    if (hasNx && this.kv.has(key)) return null;
    this.kv.set(key, value);
    return 'OK';
  }

  async get(key: string): Promise<string | null> {
    return this.kv.get(key) ?? null;
  }

  async del(...keys: string[]): Promise<number> {
    let count = 0;
    for (const k of keys) {
      if (this.kv.delete(k)) count++;
    }
    return count;
  }
}

vi.mock('../../src/services/sportsService', () => ({
  getMatches: vi.fn(),
  getMatchDetails: vi.fn(),
}));

vi.mock('../../src/services/notificationService', () => ({
  getActiveSportsSubscribers: vi.fn(),
}));

vi.mock('../../src/services/fcmService', () => ({
  sendPushToTokens: vi.fn().mockResolvedValue({ sent: 1, failed: 0, transportFailed: false }),
}));

let mockRedis: InMemoryRedis | null = null;
vi.mock('../../src/config/redis', () => ({
  getRedisClient: vi.fn(() => mockRedis),
}));

import { getMatches, getMatchDetails } from '../../src/services/sportsService';
import { getActiveSportsSubscribers } from '../../src/services/notificationService';
import { sendPushToTokens } from '../../src/services/fcmService';

const makeBat = (name: string, runs: number, balls = 30): BatsmanLive => ({
  name,
  runs,
  balls,
  fours: 4,
  sixes: 1,
  strikeRate: 120,
  onStrike: true,
});

const makeSubscriber = (overrides?: Partial<ActiveSubscriber>): ActiveSubscriber => ({
  userId: 'user1',
  tokens: ['fcm-token-1234567890'],
  followedTeamIds: new Set(['2']),
  followedShortNames: new Set(['ind']),
  followedTeamNames: new Set(['india']),
  preferences: {
    enabled: true,
    matchStartingSoon: true,
    matchStarted: true,
    matchConcluded: true,
    milestones: true,
    matchUpdates: true,
  },
  ...overrides,
});

const makeMatch = (overrides?: Partial<MatchSummary>): MatchSummary => ({
  id: 'm101',
  slug: 'ind-vs-aus',
  series: 'Border-Gavaskar Trophy',
  seriesId: 's1',
  matchDesc: '1st Test',
  format: 'Test',
  team1: { id: '2', name: 'India', shortName: 'IND', flagUrl: null },
  team2: { id: '4', name: 'Australia', shortName: 'AUS', flagUrl: null },
  team1Score: '',
  team2Score: '',
  venue: 'Perth',
  status: 'Starts soon',
  state: 'upcoming',
  isLive: false,
  startTimestamp: 1_000_000_000,
  endTimestamp: null,
  ...overrides,
});

describe('sportsNotifier helpers', () => {
  it('classifies toss, stumps, breaks, rain/delay and produces semantic keys', () => {
    expect(classifyMatchUpdateKind('India opt to bat')).toBe('toss');
    expect(classifyMatchUpdateKind('Day 1: Stumps - IND 210/4')).toBe('stumps');
    expect(classifyMatchUpdateKind('Lunch Break - Day 2')).toBe('break');
    expect(classifyMatchUpdateKind('Play delayed due to rain')).toBe('delay');
    expect(classifyMatchUpdateKind('IND 145/2 (32.4 ov)')).toBeNull();

    const now = Date.UTC(2026, 10, 27, 10, 0, 0);
    expect(buildSemanticUpdateKey('m1', 'toss', 'India opt to bat', now)).toBe('m1:upd:toss');
    expect(buildSemanticUpdateKey('m1', 'stumps', 'Day 2: Stumps - IND trail', now)).toBe(
      'm1:upd:stumps:day2',
    );
    expect(buildSemanticUpdateKey('m1', 'break', 'Lunch Break - Day 1', now)).toBe(
      'm1:upd:break:lunch:day1',
    );
  });

  it('defines first ball strictly via scored runs/wickets or overs > 0 (ignoring Yet to bat / status ov)', () => {
    expect(
      hasFirstBallBeenBowled({ team1Score: 'Yet to bat', team2Score: 'Yet to bat', status: 'Match reduced to (18 ov) due to rain' }),
    ).toBe(false);
    expect(
      hasFirstBallBeenBowled({ team1Score: '', team2Score: '', status: 'India opt to bat' }),
    ).toBe(false);
    expect(
      hasFirstBallBeenBowled({ team1Score: '0/0 (0.0)', team2Score: 'Yet to bat', status: '' }),
    ).toBe(false);
    expect(
      hasFirstBallBeenBowled({ team1Score: '0/0 (0.1)', team2Score: 'Yet to bat', status: 'Play started' }),
    ).toBe(true);
    expect(
      hasFirstBallBeenBowled({ team1Score: '4-0 (0.1)', team2Score: 'Yet to bat', status: 'IND 4/0' }),
    ).toBe(true);
  });

  it('computes stumps resume time at next day first ball - 10m and uses 5m polls inside morning window', () => {
    const start = Date.UTC(2026, 10, 26, 4, 0, 0); // Day 1 04:00
    const stumpsEvening = Date.UTC(2026, 10, 26, 11, 30, 0);
    expect(computeStumpsResumeAt(start, stumpsEvening)).toBe(Date.UTC(2026, 10, 27, 3, 50, 0));

    // Waking up on Day 2 03:50 while status still reads "Stumps" polls in 5m instead of skipping to Day 3!
    const day2Wake = Date.UTC(2026, 10, 27, 3, 50, 0);
    expect(computeStumpsResumeAt(start, day2Wake)).toBe(day2Wake + 5 * 60 * 1000);

    expect(computeStumpsResumeAt(null, stumpsEvening)).toBe(stumpsEvening + 30 * 60 * 1000);
  });

  it('detects hot zones and emits only 100 when a slow poll jumps past both 50 and 100', () => {
    expect(isBatterInMilestoneZone([makeBat('Kohli', 34)])).toBe(false);
    expect(isBatterInMilestoneZone([makeBat('Kohli', 35)])).toBe(true);
    expect(isBatterInMilestoneZone([makeBat('Kohli', 50)])).toBe(false);
    expect(isBatterInMilestoneZone([makeBat('Kohli', 85)])).toBe(true);

    expect(detectBatterMilestones({ kohli: 46 }, [makeBat('Kohli', 102)], true)).toEqual([
      { name: 'Kohli', milestone: 100, runs: 102, balls: 30 },
    ]);
    // Brand-new batter joining after baseline who reaches 50 inside one 6m window still fires 50
    expect(detectBatterMilestones({ rohit: 80 }, [makeBat('Suryakumar', 52)], true)).toEqual([
      { name: 'Suryakumar', milestone: 50, runs: 52, balls: 30 },
    ]);
    // First attach mid-innings (!hasInningsBaseline) does not false-alert existing 50+ scores
    expect(detectBatterMilestones({}, [makeBat('Suryakumar', 64)], false)).toEqual([]);
  });

  it('matches subscribers by teamId, shortName, or teamName case-insensitively', () => {
    const sub = makeSubscriber({
      followedTeamIds: new Set(['999']),
      followedShortNames: new Set(['rcb']),
      followedTeamNames: new Set(['royal challengers']),
    });
    expect(
      subscriberMatchesMatch(sub, {
        team1Id: '1',
        team1Short: 'RCB',
        team1Name: 'Royal Challengers Bengaluru',
        team2Id: '2',
        team2Short: 'CSK',
        team2Name: 'Chennai Super Kings',
      }),
    ).toBe(true);
  });
});

describe('SportsNotifier adaptive scheduler', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRedis = new InMemoryRedis();
    vi.mocked(sendPushToTokens).mockResolvedValue({
      sent: 1,
      failed: 0,
      transportFailed: false,
    });
  });

  it('does not run when Redis is down or disconnected', async () => {
    mockRedis = null;
    const notifier = new SportsNotifier();
    await notifier.tick(Date.now());
    expect(getActiveSportsSubscribers).not.toHaveBeenCalled();
    expect(getMatches).not.toHaveBeenCalled();

    mockRedis = new InMemoryRedis();
    mockRedis.status = 'reconnecting';
    await notifier.tick(Date.now());
    expect(getActiveSportsSubscribers).not.toHaveBeenCalled();
  });

  it('makes zero Cricbuzz calls when there are no active subscribers', async () => {
    vi.mocked(getActiveSportsSubscribers).mockResolvedValue([]);
    const notifier = new SportsNotifier();

    await notifier.tick(Date.now());

    expect(getMatches).not.toHaveBeenCalled();
    expect(getMatchDetails).not.toHaveBeenCalled();
    expect(sendPushToTokens).not.toHaveBeenCalled();
  });

  it('skips 6m milestone details and uses 10m liveLiteWatch when followers of that match disabled milestones', async () => {
    const t0 = Date.now();
    // User A follows IND with milestones OFF, User B follows ENG with milestones ON
    vi.mocked(getActiveSportsSubscribers).mockResolvedValue([
      makeSubscriber({
        userId: 'u_ind',
        tokens: ['tok-ind'],
        followedTeamIds: new Set(['2']),
        followedShortNames: new Set(['ind']),
        followedTeamNames: new Set(['india']),
        preferences: {
          enabled: true,
          matchStartingSoon: true,
          matchStarted: true,
          matchConcluded: true,
          milestones: false,
          matchUpdates: true,
        },
      }),
      makeSubscriber({
        userId: 'u_eng',
        tokens: ['tok-eng'],
        followedTeamIds: new Set(['9']),
        followedShortNames: new Set(['eng']),
        followedTeamNames: new Set(['england']),
        preferences: {
          enabled: true,
          matchStartingSoon: true,
          matchStarted: true,
          matchConcluded: true,
          milestones: true,
          matchUpdates: true,
        },
      }),
    ]);

    vi.mocked(getMatches).mockImplementation(async (filter) => {
      if (filter === 'upcoming') return { matches: [], cached: false };
      return {
        matches: [
          makeMatch({
            id: 'm101',
            state: 'live',
            isLive: true,
            team1Score: '45/1 (6.0 ov)',
            status: 'IND 45/1 (6.0 ov)',
          }),
        ],
        cached: false,
      };
    });

    const notifier = new SportsNotifier();
    await notifier.tick(t0); // 6h discovery finds already-live m101

    // Should NOT schedule milestone:m101; should schedule liveLiteWatch at t0 + 10m
    expect(await mockRedis!.zscore('sports:notif:due', 'milestone:m101')).toBeNull();
    const liteScore = Number(await mockRedis!.zscore('sports:notif:due', 'liveLiteWatch'));
    expect(liteScore).toBe(t0 + 10 * 60 * 1000);

    // Next tick at +3 min does ZERO Cricbuzz calls (not 2.5m polling!)
    vi.clearAllMocks();
    await notifier.tick(t0 + 3 * 60 * 1000);
    expect(getMatches).not.toHaveBeenCalled();
    expect(getMatchDetails).not.toHaveBeenCalled();

    // Tick at +10 min calls getMatches('live') once and never calls getMatchDetails for m101
    vi.mocked(getMatches).mockResolvedValue({
      matches: [
        makeMatch({
          id: 'm101',
          state: 'live',
          isLive: true,
          team1Score: '102/1 (14.0 ov)',
          status: 'IND 102/1 (14.0 ov)',
        }),
      ],
      cached: false,
    });
    await notifier.tick(t0 + 10 * 60 * 1000);
    expect(getMatches).toHaveBeenCalledWith('live');
    expect(getMatchDetails).not.toHaveBeenCalled();
  });

  it('uses getMatchDetails alone after first ball when milestones ON, restores expired slug, and retries failed result', async () => {
    const t0 = Date.now();
    const matchStart = t0 + 2 * 60 * 60 * 1000;
    vi.mocked(getActiveSportsSubscribers).mockResolvedValue([makeSubscriber()]);

    // 1. 6h discovery sweep finds upcoming m101
    vi.mocked(getMatches).mockImplementation(async (filter) => ({
      matches: filter === 'upcoming' ? [makeMatch({ startTimestamp: matchStart })] : [],
      cached: false,
    }));
    const notifier = new SportsNotifier();
    await notifier.tick(t0);

    // No Start Watch false fire on "Yet to bat" after toss
    vi.mocked(getMatches).mockResolvedValue({
      matches: [
        makeMatch({
          startTimestamp: matchStart,
          state: 'live',
          isLive: true,
          team1Score: 'Yet to bat',
          team2Score: 'Yet to bat',
          status: 'India opt to bat',
        }),
      ],
      cached: false,
    });
    await notifier.tick(matchStart - 3 * 60 * 1000);
    expect(sendPushToTokens).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ eventType: 'match_started' }),
    );
    // And milestone must NOT be scheduled prematurely before first ball
    expect(await mockRedis!.zscore('sports:notif:due', 'milestone:m101')).toBeNull();

    vi.mocked(getMatches).mockResolvedValue({
      matches: [
        makeMatch({
          startTimestamp: matchStart,
          state: 'live',
          isLive: true,
          team1Score: '4/0 (0.2 ov)',
          status: 'IND 4/0 (0.2 ov)',
        }),
      ],
      cached: false,
    });
    await notifier.tick(matchStart);
    expect(sendPushToTokens).toHaveBeenCalledWith(
      ['fcm-token-1234567890'],
      expect.objectContaining({ eventType: 'match_started' }),
    );

    // Ensure no liveLiteWatch or startWatch:m101 remains scheduled
    expect(await mockRedis!.zscore('sports:notif:due', 'startWatch:m101')).toBeNull();
    expect(await mockRedis!.zscore('sports:notif:due', 'liveLiteWatch')).toBeNull();
    vi.mocked(getMatches).mockClear();
    vi.mocked(sendPushToTokens).mockClear();

    // 3. Simulate 24h slug key expired before Day 2 milestone poll -> notifier restores sports:slug:m101
    await mockRedis!.del('sports:slug:m101');
    const baseDetails: MatchDetails = {
      ...makeMatch({
        startTimestamp: matchStart,
        state: 'live',
        isLive: true,
        team1Score: '180/1 (25.0 ov)',
        status: 'IND 180/1 (25.0 ov)',
      }),
      playerOfMatch: null,
      liveScore: { battingTeam: 'IND', score: '180/1', runRate: '7.2', requiredRunRate: '' },
      currentBatsmen: [makeBat('Virat Kohli', 45, 35)],
      currentBowlers: [],
      partnership: '',
      lastWicket: '',
      recentBalls: '',
      toss: 'India opt to bat',
      oversLeft: '',
      latestPerformance: [],
      commentary: [],
    };
    await notifier.onLiveDetailsObserved(baseDetails);
    await mockRedis!.zadd('sports:notif:due', String(matchStart + 6 * 60 * 1000), 'milestone:m101');
    vi.mocked(getMatchDetails).mockResolvedValue({
      match: {
        ...baseDetails,
        status: 'Lunch Break - Day 2',
        currentBatsmen: [makeBat('Virat Kohli', 101, 70)],
      },
      cached: false,
    });
    await notifier.tick(matchStart + 6 * 60 * 1000);

    // Slug was restored AND getMatches('live') was NOT called!
    expect(await mockRedis!.get('sports:slug:m101')).toBe('ind-vs-aus');
    expect(getMatches).not.toHaveBeenCalled();
    expect(sendPushToTokens).toHaveBeenCalledWith(
      ['fcm-token-1234567890'],
      expect.objectContaining({ eventType: 'match_update', body: 'Lunch Break - Day 2' }),
    );
    expect(sendPushToTokens).toHaveBeenCalledWith(
      ['fcm-token-1234567890'],
      expect.objectContaining({
        eventType: 'milestone',
        title: expect.stringContaining('CENTURY! Virat Kohli 101(70)'),
      }),
    );

    vi.mocked(sendPushToTokens).mockClear();

    // 4. Concluded result handled straight from getMatchDetails + retried on FCM transport failure
    vi.mocked(getMatchDetails).mockResolvedValue({
      match: {
        ...baseDetails,
        state: 'completed',
        isLive: false,
        endTimestamp: matchStart + 12 * 60 * 1000,
        status: 'India won by 6 wickets',
      },
      cached: false,
    });
    vi.mocked(sendPushToTokens).mockResolvedValueOnce({
      sent: 0,
      failed: 1,
      transportFailed: true,
    });
    await notifier.tick(matchStart + 12 * 60 * 1000);
    expect(sendPushToTokens).toHaveBeenCalledTimes(1);

    vi.mocked(sendPushToTokens).mockResolvedValueOnce({
      sent: 1,
      failed: 0,
      transportFailed: false,
    });
    await notifier.tick(matchStart + 13 * 60 * 1000);
    expect(sendPushToTokens).toHaveBeenCalledTimes(2);
    expect(sendPushToTokens).toHaveBeenLastCalledWith(
      ['fcm-token-1234567890'],
      expect.objectContaining({
        eventType: 'match_concluded',
        body: 'India won by 6 wickets',
      }),
    );
  });

  it('schedules startSoon when ONLY matchStartingSoon is enabled, and notifies again in Test 2nd innings', async () => {
    const t0 = Date.now();
    const matchStart = t0 + 2 * 60 * 60 * 1000;
    vi.mocked(getActiveSportsSubscribers).mockResolvedValue([
      makeSubscriber({
        preferences: {
          enabled: true,
          matchStartingSoon: true,
          matchStarted: false,
          matchConcluded: false,
          milestones: false,
          matchUpdates: false,
        },
      }),
    ]);
    vi.mocked(getMatches).mockImplementation(async (filter) => ({
      matches: filter === 'upcoming' ? [makeMatch({ startTimestamp: matchStart })] : [],
      cached: false,
    }));

    const notifier = new SportsNotifier();
    await notifier.tick(t0);
    expect(await mockRedis!.zscore('sports:notif:due', 'startSoon:m101')).not.toBeNull();
    expect(await mockRedis!.zscore('sports:notif:due', 'startWatch:m101')).toBeNull();

    // Now test Innings 2 milestone reset for same player
    mockRedis = new InMemoryRedis();
    vi.mocked(sendPushToTokens).mockClear();
    vi.mocked(getActiveSportsSubscribers).mockResolvedValue([makeSubscriber()]);
    const innNotifier = new SportsNotifier();
    const inn1Details: MatchDetails = {
      ...makeMatch({
        id: 'm_test',
        startTimestamp: matchStart,
        state: 'live',
        isLive: true,
        team1Score: '140/2 (30.0)',
        team2Score: 'Yet to bat',
        status: 'IND 140/2',
      }),
      playerOfMatch: null,
      liveScore: { battingTeam: 'IND', score: '140/2', runRate: '4.6', requiredRunRate: '' },
      currentBatsmen: [makeBat('Virat Kohli', 48, 60)],
      currentBowlers: [],
      partnership: '',
      lastWicket: '',
      recentBalls: '',
      toss: 'India opt to bat',
      oversLeft: '',
      latestPerformance: [],
      commentary: [],
    };

    vi.mocked(getMatches).mockImplementation(async (filter) => ({
      matches: filter === 'live' ? [inn1Details] : [],
      cached: false,
    }));
    await innNotifier.tick(t0); // discovers live m_test in innings 1
    await innNotifier.onLiveDetailsObserved(inn1Details); // baseline Virat=48 in inn 1

    // Virat hits 55 in innings 1 -> notifies
    await innNotifier.onLiveDetailsObserved({
      ...inn1Details,
      currentBatsmen: [makeBat('Virat Kohli', 55, 65)],
    });
    expect(sendPushToTokens).toHaveBeenCalledTimes(1);

    // Later in India's 2nd innings (team1Score = "320 & 120/2", team2Score = "290") -> Virat hits 51 again -> notifies!
    vi.mocked(sendPushToTokens).mockClear();
    await innNotifier.onLiveDetailsObserved({
      ...inn1Details,
      team1Score: '320 & 90/2 (22.0)',
      team2Score: '290 (85.0)',
      currentBatsmen: [makeBat('Virat Kohli', 42, 40)],
    });
    await innNotifier.onLiveDetailsObserved({
      ...inn1Details,
      team1Score: '320 & 110/2 (25.0)',
      team2Score: '290 (85.0)',
      currentBatsmen: [makeBat('Virat Kohli', 51, 49)],
    });
    expect(sendPushToTokens).toHaveBeenCalledWith(
      ['fcm-token-1234567890'],
      expect.objectContaining({
        eventType: 'milestone',
        title: expect.stringContaining('FIFTY! Virat Kohli 51(49)'),
      }),
    );
  });
});
