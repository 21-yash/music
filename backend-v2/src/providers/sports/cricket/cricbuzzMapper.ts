/**
 * Cricbuzz → normalized type mapper.
 *
 * Transforms the raw JSON objects extracted from Cricbuzz's RSC chunks
 * into our app-owned types from `../types.ts`.
 */

import type {
  MatchSummary,
  MatchDetails,
  MatchState,
  MatchFormat,
  Team,
  LiveScore,
  BatsmanLive,
  BowlerLive,
  CommentaryEntry,
  Scorecard,
  InningsScorecard,
  BatsmanInnings,
  BowlerInnings,
  FallOfWicket,
  Powerplay,
  Squad,
  PlayerRef,
} from '../types';
import { buildFlagUrl, buildMatchSlug, buildPlayerImageUrl } from './cricbuzzScraper';

// ─── Match list mapping ──────────────────────────────────────────────

/**
 * States that indicate a match is NOT live.
 */
const NON_LIVE_STATES = new Set([
  'preview', 'upcoming', 'scheduled', 'complete', 'result', 'abandoned', 'stumps',
]);

/**
 * States that indicate a match is upcoming (not yet started).
 */
const UPCOMING_STATES = new Set([
  'preview', 'upcoming', 'upcoming match', 'scheduled',
]);



/**
 * Format a single innings score → "runs/wickets (overs)" string.
 */
function fmtInnings(inn: Record<string, unknown> | undefined): string | null {
  if (!inn) return null;
  const w = inn.wickets != null ? `/${inn.wickets}` : '';
  const o = inn.overs != null ? ` (${inn.overs})` : '';
  return inn.runs != null ? `${inn.runs}${w}${o}` : null;
}

/**
 * Format both innings of a team into a combined score string.
 */
function buildTeamScore(
  teamScore: Record<string, unknown> | undefined,
  fallback = 'Yet to bat',
): string {
  if (!teamScore) return fallback;
  const inngs1 = fmtInnings(teamScore.inngs1 as Record<string, unknown> | undefined);
  const inngs2 = fmtInnings(teamScore.inngs2 as Record<string, unknown> | undefined);
  return [inngs1, inngs2].filter(Boolean).join(' & ') || fallback;
}

/**
 * Build a Team object from Cricbuzz raw team data.
 */
function buildTeam(raw: Record<string, unknown>): Team {
  return {
    id: String(raw.teamId || raw.id || ''),
    name: String(raw.teamName || raw.name || ''),
    shortName: String(raw.teamSName || raw.shortName || raw.teamName || raw.name || ''),
    flagUrl: buildFlagUrl(
      raw.imageId as string | number | undefined,
      String(raw.teamName || raw.name || ''),
    ),
  };
}

/**
 * Classify a match format from Cricbuzz's matchFormat/matchType strings.
 */
function classifyFormat(format: string, matchType: string): MatchFormat {
  const f = (format || matchType || '').toUpperCase();
  if (f.includes('TEST')) return 'Test';
  if (f.includes('ODI')) return 'ODI';
  if (f.includes('T20I') || f.includes('T20I')) return 'T20I';
  if (f.includes('T20')) return 'T20';
  return 'Other';
}

/**
 * Determine the match state from Cricbuzz state/stateTitle.
 */
function classifyState(state: string, stateTitle: string): MatchState {
  const s = state.toLowerCase().trim();
  const st = stateTitle.toLowerCase().trim();

  if (UPCOMING_STATES.has(s) || UPCOMING_STATES.has(st)) return 'upcoming';
  if (s === 'abandoned' || st === 'abandoned') return 'abandoned';
  if (s === 'complete' || s === 'result' || st === 'complete' || st === 'result') return 'completed';
  if (!NON_LIVE_STATES.has(s) && !NON_LIVE_STATES.has(st)) return 'live';
  return 'completed';
}

/**
 * Map a raw Cricbuzz match wrapper (from typeMatches) to a MatchSummary.
 */
export function mapMatchSummary(
  matchWrapper: Record<string, unknown>,
  seriesName: string,
  seriesId: string,
  matchType: string,
): MatchSummary | null {
  const info = matchWrapper.matchInfo as Record<string, unknown> | undefined;
  const score = matchWrapper.matchScore as Record<string, unknown> | undefined;
  if (!info) return null;

  const team1Raw = (info.team1 || {}) as Record<string, unknown>;
  const team2Raw = (info.team2 || {}) as Record<string, unknown>;
  const venue = (info.venueInfo || {}) as Record<string, unknown>;

  const stateStr = String(info.state || '');
  const stateTitleStr = String(info.stateTitle || '');
  const state = classifyState(stateStr, stateTitleStr);

  const team1 = buildTeam(team1Raw);
  const team2 = buildTeam(team2Raw);

  const matchId = String(info.matchId || '');
  const slug = buildMatchSlug(
    team1.shortName || team1.name,
    team2.shortName || team2.name,
    String(info.matchDesc || 'match'),
    seriesName,
  );

  const venueStr =
    venue.ground && venue.city
      ? `${venue.ground}, ${venue.city}`
      : String(venue.ground || venue.city || '');

  return {
    id: matchId,
    slug,
    series: seriesName,
    seriesId,
    matchDesc: String(info.matchDesc || ''),
    format: classifyFormat(String(info.matchFormat || ''), matchType),
    team1,
    team2,
    team1Score: buildTeamScore(
      (score?.team1Score as Record<string, unknown>) || undefined,
    ),
    team2Score: buildTeamScore(
      (score?.team2Score as Record<string, unknown>) || undefined,
    ),
    venue: venueStr,
    status: String(info.status || 'Scheduled'),
    state,
    isLive: state === 'live',
    startTimestamp: info.startDate ? parseInt(String(info.startDate), 10) : null,
    endTimestamp: info.endDate ? parseInt(String(info.endDate), 10) : null,
  };
}

/**
 * Extract all match summaries from a parsed typeMatches array.
 *
 * @param filter - Which matches to include
 */
export function mapTypeMatchesToSummaries(
  typeMatches: unknown[],
  filter: 'live' | 'upcoming' | 'recent',
): MatchSummary[] {
  const matches: MatchSummary[] = [];

  for (const typeGroup of typeMatches) {
    const group = typeGroup as Record<string, unknown>;
    const matchType = String(group.matchType || 'Unknown');

    for (const seriesWrapper of (group.seriesMatches || []) as Record<string, unknown>[]) {
      const series = seriesWrapper?.seriesAdWrapper as Record<string, unknown> | undefined;
      if (!series) continue;

      const seriesName = String(series.seriesName || '');
      const seriesId = String(series.seriesId || '');

      for (const matchWrapper of (series.matches || []) as Record<string, unknown>[]) {
        const summary = mapMatchSummary(matchWrapper, seriesName, seriesId, matchType);
        if (!summary) continue;

        // Apply filter
        if (filter === 'live' && !summary.isLive) continue;
        if (filter === 'upcoming' && summary.state !== 'upcoming') continue;
        if (filter === 'recent' && summary.state !== 'completed' && summary.state !== 'abandoned' && summary.state !== 'no_result') continue;

        matches.push(summary);
      }
    }
  }

  // Sort: live matches first, then by start time
  if (filter === 'live') {
    matches.sort((a, b) => (a.isLive === b.isLive ? 0 : a.isLive ? -1 : 1));
  } else if (filter === 'upcoming') {
    matches.sort((a, b) => (a.startTimestamp || 0) - (b.startTimestamp || 0));
  } else {
    // Recent: most recently ended first
    matches.sort((a, b) => (b.endTimestamp || 0) - (a.endTimestamp || 0));
  }

  return matches;
}

// ─── Match details mapping ───────────────────────────────────────────

/**
 * Map Cricbuzz's commentaryPageData to MatchDetails.
 */
export function mapMatchDetails(
  commentaryData: Record<string, unknown>,
  matchId: string,
  slug: string,
): MatchDetails | null {
  const header = (commentaryData.matchHeader || {}) as Record<string, unknown>;
  const miniscore = (commentaryData.miniscore || {}) as Record<string, unknown>;
  const comments = (commentaryData.matchCommentary || {}) as Record<string, unknown>;

  if (!header.matchId && !header.team1) return null;

  const team1Raw = (header.team1 || {}) as Record<string, unknown>;
  const team2Raw = (header.team2 || {}) as Record<string, unknown>;
  const team1 = buildTeam(team1Raw);
  const team2 = buildTeam(team2Raw);

  const stateStr = String(header.state || miniscore.state || '');
  const stateTitleStr = String(header.stateTitle || '');
  const state = classifyState(stateStr, stateTitleStr);

  const tossResults = header.tossResults as Record<string, unknown> | undefined;
  const toss = tossResults
    ? `${tossResults.tossWinnerName || ''} (${tossResults.decision || ''})`
    : '';

  // Player of the match
  let playerOfMatch: PlayerRef | null = null;
  const pomList = header.playersOfTheMatch as Record<string, unknown>[] | undefined;
  if (pomList?.length) {
    const pom = pomList[0];
    playerOfMatch = {
      id: String(pom.id || ''),
      name: String(pom.fullName || pom.name || ''),
      role: '',
      imageUrl: buildPlayerImageUrl(pom.faceImageId as string | number | undefined),
      isCaptain: false,
      isKeeper: false,
    };
  }

  // Teams & scores — group multi-innings Tests
  const matchScoreDetails = miniscore.matchScoreDetails as Record<string, unknown> | undefined;
  const inningsList = ((matchScoreDetails?.inningsScoreList || []) as Record<string, unknown>[]);

  const teamDataMap: Record<string, { name: string; scores: string[]; isBatting: boolean }> = {};

  for (const inn of inningsList) {
    const batTeamName = String(inn.batTeamName || '');
    if (!teamDataMap[batTeamName]) {
      teamDataMap[batTeamName] = { name: batTeamName, scores: [], isBatting: false };
    }
    let innScore = String(inn.score || 0);
    if (inn.wickets !== undefined) innScore += `-${inn.wickets}`;
    if (inn.overs !== undefined) innScore += ` (${inn.overs})`;
    if (inn.isDeclared) innScore += ' d';
    teamDataMap[batTeamName].scores.push(innScore);
  }

  // Mark currently batting team
  const batTeam = miniscore.batTeam as Record<string, unknown> | undefined;
  let currentBatTeamName = '';
  if (batTeam) {
    const matchedInnings = inningsList.find(
      (i) => i.batTeamId === batTeam.teamId,
    );
    currentBatTeamName = String(matchedInnings?.batTeamName || '');
    if (currentBatTeamName && teamDataMap[currentBatTeamName]) {
      teamDataMap[currentBatTeamName].isBatting = true;
    }
  }

  // Build score strings
  const team1Score = teamDataMap[team1.name]?.scores.join(' & ')
    || teamDataMap[team1.shortName]?.scores.join(' & ')
    || 'Yet to bat';
  const team2Score = teamDataMap[team2.name]?.scores.join(' & ')
    || teamDataMap[team2.shortName]?.scores.join(' & ')
    || 'Yet to bat';

  // Live score
  let liveScore: LiveScore | null = null;
  if (currentBatTeamName && teamDataMap[currentBatTeamName]) {
    const lastScore = teamDataMap[currentBatTeamName].scores.slice(-1)[0] || '';
    liveScore = {
      battingTeam: currentBatTeamName,
      score: lastScore,
      runRate: miniscore.currentRunRate ? String(miniscore.currentRunRate) : '',
      requiredRunRate: miniscore.requiredRunRate ? String(miniscore.requiredRunRate) : '',
    };
  }

  // Current batsmen
  const currentBatsmen: BatsmanLive[] = [];
  const parseBatter = (b: Record<string, unknown> | undefined, onStrike: boolean): BatsmanLive | null => {
    if (!b?.id) return null;
    return {
      name: String(b.name || ''),
      runs: Number(b.runs || 0),
      balls: Number(b.balls || 0),
      fours: Number(b.fours || 0),
      sixes: Number(b.sixes || 0),
      strikeRate: Number(b.strikeRate || 0),
      onStrike,
    };
  };
  const striker = parseBatter(miniscore.batsmanStriker as Record<string, unknown> | undefined, true);
  const nonStriker = parseBatter(miniscore.batsmanNonStriker as Record<string, unknown> | undefined, false);
  if (striker) currentBatsmen.push(striker);
  if (nonStriker) currentBatsmen.push(nonStriker);

  // Current bowlers
  const currentBowlers: BowlerLive[] = [];
  const parseBowler = (b: Record<string, unknown> | undefined): BowlerLive | null => {
    if (!b?.id) return null;
    return {
      name: String(b.name || ''),
      overs: String(b.overs || 0),
      maidens: Number(b.maidens || 0),
      runs: Number(b.runs || 0),
      wickets: Number(b.wickets || 0),
      economy: Number(b.economy || 0),
    };
  };
  const bowler1 = parseBowler(miniscore.bowlerStriker as Record<string, unknown> | undefined);
  const bowler2 = parseBowler(miniscore.bowlerNonStriker as Record<string, unknown> | undefined);
  if (bowler1) currentBowlers.push(bowler1);
  if (bowler2) currentBowlers.push(bowler2);

  // Partnership
  const partnerShip = miniscore.partnerShip as Record<string, unknown> | undefined;
  const partnership = partnerShip
    ? `${partnerShip.runs || 0}(${partnerShip.balls || 0})`
    : '';

  // Commentary — cap at 6 entries
  const commentary: CommentaryEntry[] = [];
  if (comments && typeof comments === 'object') {
    const timestamps = Object.keys(comments)
      .sort((a, b) => Number(b) - Number(a))
      .slice(0, 20); // scan cap

    for (const ts of timestamps) {
      const comm = (comments as Record<string, Record<string, unknown>>)[ts];
      if (!comm?.commText) continue;
      const commText = String(comm.commText);
      if (commText.includes('comes to the crease')) continue;

      let event: CommentaryEntry['event'] = '';
      if (Array.isArray(comm.event)) {
        if (comm.event.includes('wicket')) event = 'wicket';
        else if (comm.event.includes('four')) event = 'four';
        else if (comm.event.includes('six')) event = 'six';
      }

      commentary.push({
        ball: (comm.ballMetric === '$undefined' || !comm.ballMetric) ? '' : String(comm.ballMetric),
        event,
        text: commText.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim(),
      });

      if (commentary.length >= 6) break;
    }
  }
    const venueInfo: any = header.venueInfo || {};
    const venue = [venueInfo.ground, venueInfo.city].filter(Boolean).join(', ');

  const rawOversRem = String(miniscore.oversRem || '');
  const oversLeft = (rawOversRem && rawOversRem !== "undefined" && rawOversRem !== "$undefined") ? rawOversRem : '';

  return {
    id: matchId,
    slug,
    series: String(header.seriesName || ''),
    seriesId: String(header.seriesId || ''),
    matchDesc: String(header.matchDescription || ''),
    format: classifyFormat(String(header.matchFormat || ''), ''),
    team1,
    team2,
    team1Score,
    team2Score,
    venue: venue,
    status: String(header.status || miniscore.status || ''),
    state,
    isLive: state === 'live',
    startTimestamp: header.matchStartTimestamp ? Number(header.matchStartTimestamp) : null,
    endTimestamp: header.matchCompleteTimestamp ? Number(header.matchCompleteTimestamp) : null,
    playerOfMatch,
    liveScore,
    currentBatsmen,
    currentBowlers,
    partnership,
    lastWicket: String(miniscore.lastWicket || ''),
    recentBalls: String(miniscore.recentOvsStats || ''),
    toss,
    oversLeft: oversLeft,
    latestPerformance: (miniscore.latestPerformance as any[])?.map((lp: any) => ({
      runs: Number(lp.runs || 0),
      wkts: Number(lp.wkts || 0),
      label: String(lp.label || '')
    })) || [],
    commentary,
  };
}

// ─── Scorecard mapping ───────────────────────────────────────────────

/**
 * Map Cricbuzz's scorecardApiData + matchHeader to a Scorecard.
 */
export function mapScorecard(
  scorecardApiData: Record<string, unknown>,
  matchHeader: Record<string, unknown>,
  matchId: string,
): Scorecard | null {
  const scoreCards = scorecardApiData.scoreCard as Record<string, unknown>[] | undefined;
  if (!scoreCards) return null;

  const venue = matchHeader.venue as Record<string, unknown> | undefined;
  const scorecard: Scorecard = {
    matchId,
    matchInfo: {
      title: `${(matchHeader.team1 as Record<string, unknown>)?.name || ''} vs ${(matchHeader.team2 as Record<string, unknown>)?.name || ''}, ${matchHeader.matchDescription || ''}`,
      series: String(matchHeader.seriesName || ''),
      venue: venue ? `${venue.name || ''}, ${venue.city || ''}` : '',
      dateTime: matchHeader.matchStartTimestamp
        ? new Date(Number(matchHeader.matchStartTimestamp)).toLocaleString('en-IN', {
            timeZone: 'Asia/Kolkata',
            dateStyle: 'medium',
            timeStyle: 'short',
          }) + ' IST'
        : '',
    },
    status: String(matchHeader.status || ''),
    innings: [],
  };

  for (let index = 0; index < scoreCards.length; index++) {
    const inn = scoreCards[index];
    const batTeam = (inn.batTeamDetails || {}) as Record<string, unknown>;
    const bowlTeam = (inn.bowlTeamDetails || {}) as Record<string, unknown>;
    const scoreDet = (inn.scoreDetails || {}) as Record<string, unknown>;
    const extras = (inn.extrasData || {}) as Record<string, unknown>;
    const wkts = (inn.wicketsData || {}) as Record<string, unknown>;
    const pp = (inn.ppData || {}) as Record<string, unknown>;

    const allBatsmen = Object.values((batTeam.batsmenData || {}) as Record<string, Record<string, unknown>>);
    const battedPlayers = allBatsmen.filter((b) => b.outDesc);
    const didNotBatPlayers = allBatsmen.filter((b) => !b.outDesc).map((b) => String(b.batName));

    const batting: BatsmanInnings[] = battedPlayers.map((b) => ({
      name: String(b.batName || ''),
      status: String(b.outDesc || ''),
      runs: Number(b.runs || 0),
      balls: Number(b.balls || 0),
      fours: Number(b.fours || 0),
      sixes: Number(b.sixes || 0),
      strikeRate: Number(b.strikeRate || 0),
    }));

    const bowlersData = (bowlTeam.bowlersData || {}) as Record<string, Record<string, unknown>>;
    const bowling: BowlerInnings[] = Object.values(bowlersData).map((b) => ({
      name: String(b.bowlName || ''),
      overs: String(b.overs || 0),
      maidens: Number(b.maidens || 0),
      runs: Number(b.runs || 0),
      wickets: Number(b.wickets || 0),
      noBalls: Number(b.no_balls || 0),
      wides: Number(b.wides || 0),
      economy: Number(b.economy || 0),
    }));

    const fallOfWickets: FallOfWicket[] = Object.values(wkts as Record<string, Record<string, unknown>>).map((w) => ({
      player: String(w.batName || ''),
      scoreAtWicket: String(w.wktRuns || ''),
      over: String(w.wktOver || ''),
    }));

    const powerplays: Powerplay[] = Object.values(pp as Record<string, Record<string, unknown>>).map((p) => ({
      type: String(p.ppType || 'Mandatory'),
      overs: `${p.ppOversFrom} - ${p.ppOversTo}`,
      runs: Number(p.runsScored || 0),
    }));

    const inningsData: InningsScorecard = {
      inningsNumber: index + 1,
      battingTeam: String(batTeam.batTeamName || 'Unknown'),
      score: `${scoreDet.runs || 0}-${scoreDet.wickets || 0}`,
      overs: String(scoreDet.overs || 0),
      batting,
      bowling,
      extras: `${extras.total || 0} (b ${extras.byes || 0}, lb ${extras.legByes || 0}, w ${extras.wides || 0}, nb ${extras.noBalls || 0}, p ${extras.penalty || 0})`,
      total: `${scoreDet.runs || 0}/${scoreDet.wickets || 0} (${scoreDet.overs || 0} Overs, RR: ${scoreDet.runRate || 0})`,
      didNotBat: didNotBatPlayers,
      fallOfWickets,
      powerplays,
    };

    scorecard.innings.push(inningsData);
  }

  return scorecard;
}

// ─── Squad mapping ───────────────────────────────────────────────────

/**
 * Map a Cricbuzz raw team squad object into PlayerRef arrays.
 */
function mapSquadTeam(
  teamData: Record<string, unknown>,
): { name: string; playingXI: PlayerRef[]; bench: PlayerRef[] } | null {
  const team = teamData.team as Record<string, unknown> | undefined;
  if (!team) return null;

  const players = teamData.players as Record<string, Record<string, unknown>[]> | undefined;

  const mapPlayer = (p: Record<string, unknown>): PlayerRef => ({
    id: String(p.id || ''),
    name: String(p.name || p.fullName || ''),
    role: String(p.role || ''),
    imageUrl: buildPlayerImageUrl(
      (p.imageDetails as Record<string, unknown> | undefined)?.imageId as string | number | undefined,
    ),
    isCaptain: !!p.captain,
    isKeeper: !!p.keeper,
  });

  return {
    name: String(team.teamName || 'Unknown'),
    playingXI: (players?.['playing XI'] || []).map(mapPlayer),
    bench: (players?.['bench'] || []).map(mapPlayer),
  };
}

/**
 * Map raw team1Data/team2Data to a Squad object.
 */
export function mapSquads(
  team1Data: Record<string, unknown>,
  team2Data: Record<string, unknown>,
  matchId: string,
): Squad | null {
  const team1 = mapSquadTeam(team1Data);
  const team2 = mapSquadTeam(team2Data);

  if (!team1 && !team2) return null;

  const teams: Squad['teams'] = {};
  if (team1) teams[team1.name] = { playingXI: team1.playingXI, bench: team1.bench };
  if (team2) teams[team2.name] = { playingXI: team2.playingXI, bench: team2.bench };

  return { matchId, teams };
}
