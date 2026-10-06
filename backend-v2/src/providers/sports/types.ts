/**
 * Normalized sports types.
 *
 * These are the app-owned models that every sports provider must map to.
 * Neither the Android client nor the rest of the backend should ever see
 * a third-party source's raw response shape — only these types.
 */

// ─── Enums ───────────────────────────────────────────────────────────

export type MatchState = 'live' | 'upcoming' | 'completed' | 'abandoned' | 'no_result';

export type MatchFormat = 'Test' | 'ODI' | 'T20I' | 'T20' | 'Other';

// ─── Core types ──────────────────────────────────────────────────────

export interface Team {
  id: string;
  name: string;           // Full name: "India"
  shortName: string;      // Abbreviation: "IND"
  flagUrl: string | null;
}

export interface MatchSummary {
  id: string;
  slug: string;
  series: string;
  seriesId: string;
  matchDesc: string;       // "1st Test", "Final", "3rd T20I"
  format: MatchFormat;
  team1: Team;
  team2: Team;
  team1Score: string;      // "518/5d & 63/1" — pre-formatted
  team2Score: string;
  venue: string;           // "Wankhede Stadium, Mumbai"
  status: string;          // "IND need 58 runs" / "India won by 5 wickets"
  state: MatchState;
  isLive: boolean;
  startTimestamp: number | null;
  endTimestamp: number | null;
}

// ─── Match details ───────────────────────────────────────────────────

export interface MatchDetails extends MatchSummary {
  playerOfMatch: PlayerRef | null;
  liveScore: LiveScore | null;
  currentBatsmen: BatsmanLive[];
  currentBowlers: BowlerLive[];
  partnership: string;
  lastWicket: string;
  recentBalls: string;
  toss: string;
  oversLeft: string;
  latestPerformance: { runs: number; wkts: number; label: string }[];
  commentary: CommentaryEntry[];
}

export interface LiveScore {
  battingTeam: string;
  score: string;
  runRate: string;
  requiredRunRate: string;
}

export interface BatsmanLive {
  name: string;
  runs: number;
  balls: number;
  fours: number;
  sixes: number;
  strikeRate: number;
  onStrike: boolean;
}

export interface BowlerLive {
  name: string;
  overs: string;
  maidens: number;
  runs: number;
  wickets: number;
  economy: number;
}

export interface CommentaryEntry {
  ball: string;
  event: 'wicket' | 'four' | 'six' | '';
  text: string;
}

// ─── Scorecard ───────────────────────────────────────────────────────

export interface Scorecard {
  matchId: string;
  matchInfo: {
    title: string;
    series: string;
    venue: string;
    dateTime: string;
  };
  status: string;
  innings: InningsScorecard[];
}

export interface InningsScorecard {
  inningsNumber: number;
  battingTeam: string;
  score: string;
  overs: string;
  batting: BatsmanInnings[];
  bowling: BowlerInnings[];
  extras: string;
  total: string;
  didNotBat: string[];
  fallOfWickets: FallOfWicket[];
  powerplays: Powerplay[];
}

export interface BatsmanInnings {
  name: string;
  status: string;        // "c Kohli b Bumrah", "not out"
  runs: number;
  balls: number;
  fours: number;
  sixes: number;
  strikeRate: number;
}

export interface BowlerInnings {
  name: string;
  overs: string;
  maidens: number;
  runs: number;
  wickets: number;
  noBalls: number;
  wides: number;
  economy: number;
}

export interface FallOfWicket {
  player: string;
  scoreAtWicket: string;
  over: string;
}

export interface Powerplay {
  type: string;
  overs: string;
  runs: number;
}

// ─── Squads ──────────────────────────────────────────────────────────

export interface Squad {
  matchId: string;
  teams: Record<string, { playingXI: PlayerRef[]; bench: PlayerRef[] }>;
}

export interface PlayerRef {
  id: string;
  name: string;
  role: string;
  imageUrl: string | null;
  isCaptain: boolean;
  isKeeper: boolean;
}
