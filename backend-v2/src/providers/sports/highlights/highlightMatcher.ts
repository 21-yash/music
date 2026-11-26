import type { MatchFormat } from '../types';

/**
 * Pure matching, normalization, and scoring helpers for cricket match highlights.
 *
 * Used across BCCI.tv, IPLT20.com, and YouTube providers so team aliases,
 * gender/age-group disambiguation (IND vs IND-W vs IND A), match ordinals,
 * and date windows behave identically everywhere.
 */

export interface MatchCandidateContext {
  team1Name: string;
  team1Short: string;
  team2Name: string;
  team2Short: string;
  series: string;
  matchDesc: string;
  format: MatchFormat;
  startTimestamp: number | null;
}

export interface VideoCandidateInput {
  title: string;
  publishedAt: string | null;
  publishedText?: string | null;
  durationSeconds: number;
}

const TEAM_ALIASES: Record<string, string[]> = {
  india: ['ind', 'india'],
  'india women': ['ind w', 'indw', 'india women', 'india w'],
  'india a': ['ind a', 'inda', 'india a'],
  'india u19': ['ind u19', 'india u19', 'india under 19'],
  australia: ['aus', 'australia'],
  'australia women': ['aus w', 'ausw', 'australia women', 'australia w'],
  'australia a': ['aus a', 'ausa', 'australia a'],
  england: ['eng', 'england'],
  'england women': ['eng w', 'engw', 'england women', 'england w'],
  'south africa': ['sa', 'rsa', 'south africa'],
  'south africa women': ['sa w', 'saw', 'rsa w', 'south africa women', 'south africa w'],
  'new zealand': ['nz', 'new zealand'],
  'new zealand women': ['nz w', 'nzw', 'new zealand women', 'new zealand w'],
  pakistan: ['pak', 'pakistan'],
  'pakistan women': ['pak w', 'pakw', 'pakistan women', 'pakistan w'],
  'sri lanka': ['sl', 'sri lanka'],
  'sri lanka women': ['sl w', 'slw', 'sri lanka women', 'sri lanka w'],
  'west indies': ['wi', 'windies', 'west indies'],
  'west indies women': ['wi w', 'wiw', 'west indies women', 'west indies w'],
  bangladesh: ['ban', 'bangladesh'],
  'bangladesh women': ['ban w', 'banw', 'bangladesh women', 'bangladesh w'],
  afghanistan: ['afg', 'afghanistan'],
  'afghanistan a': ['afg a', 'afga', 'afghanistan a'],
  'pakistan a': ['pak a', 'paka', 'pakistan a'],
  'sri lanka a': ['sl a', 'sla', 'sri lanka a'],
  'bangladesh a': ['ban a', 'bana', 'bangladesh a'],
  zimbabwe: ['zim', 'zimbabwe'],
  'zimbabwe women': ['zim w', 'zimw', 'zimbabwe women', 'zimbabwe w'],
  ireland: ['ire', 'ireland'],
  'ireland women': ['ire w', 'irew', 'ireland women', 'ireland w'],
  'hong kong': ['hk', 'hkc', 'hong kong', 'hong kong china', 'hk china'],
  scotland: ['sco', 'scotland'],
  netherlands: ['ned', 'netherlands'],
  nepal: ['nep', 'nepal'],
  usa: ['usa', 'united states'],
  uae: ['uae', 'united arab emirates'],
  namibia: ['nam', 'namibia'],
  oman: ['oma', 'oman'],
  // IPL teams
  'chennai super kings': ['csk', 'chennai', 'chennai super kings'],
  'mumbai indians': ['mi', 'mumbai', 'mumbai indians'],
  'royal challengers bengaluru': ['rcb', 'bengaluru', 'bangalore', 'royal challengers bengaluru', 'royal challengers bangalore'],
  'kolkata knight riders': ['kkr', 'kolkata', 'kolkata knight riders'],
  'gujarat titans': ['gt', 'gujarat', 'gujarat titans'],
  'rajasthan royals': ['rr', 'rajasthan', 'rajasthan royals'],
  'sunrisers hyderabad': ['srh', 'hyderabad', 'sunrisers', 'sunrisers hyderabad'],
  'delhi capitals': ['dc', 'delhi', 'delhi capitals'],
  'punjab kings': ['pbks', 'punjab', 'punjab kings'],
  'lucknow super giants': ['lsg', 'lucknow', 'lucknow super giants'],
  // Indian domestic
  'tamil nadu': ['tn', 'tnca', 'tamil nadu'],
  railways: ['rspb', 'rlY', 'railways'],
  mumbai: ['mum', 'mca', 'mumbai'],
  karnataka: ['kar', 'ksca', 'karnataka'],
  bengal: ['ben', 'cab', 'bengal'],
  saurashtra: ['sau', 'saurashtra'],
  vidarbha: ['vid', 'vidarbha'],
  'madhya pradesh': ['mp', 'madhya pradesh'],
  'uttar pradesh': ['up', 'uttar pradesh'],
  'rest of india': ['roi', 'rest of india'],
};

const NON_HIGHLIGHT_KEYWORDS = [
  'press conference',
  'post match presentation',
  'post-match presentation',
  'pre-match',
  'pre match',
  'preview',
  'net session',
  'practice session',
  'pitch report',
  'toss',
  'interview',
  'reaction',
  'dream11',
  'fantasy',
  'cricket 24',
  'cricket 22',
  'real cricket',
  'gameplay',
  'simulation',
  'live stream',
];

/**
 * Normalize free text into lowercase space-separated alphanumeric tokens.
 */
export function normalizeMatchText(text: string): string {
  return text
    .toLowerCase()
    .replace(/['’]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/**
 * Parse a "MM:SS" or "HH:MM:SS" duration string into total seconds.
 */
export function parseDurationToSeconds(duration: string | undefined | null): number {
  if (!duration) return 0;
  const parts = duration.trim().split(':').map((p) => Number.parseInt(p, 10));
  if (parts.some((p) => Number.isNaN(p) || p < 0)) return 0;
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  return 0;
}

/**
 * Build candidate lowercase alias tokens for a team name + shortName.
 */
export function getTeamAliases(name: string, shortName: string): string[] {
  const normName = normalizeMatchText(name);
  const normShort = normalizeMatchText(shortName);

  const aliases = new Set<string>();
  if (normName) aliases.add(normName);
  if (normShort) aliases.add(normShort);

  for (const [canonical, list] of Object.entries(TEAM_ALIASES)) {
    const normalizedList = list.map(normalizeMatchText);
    if (
      normalizedList.includes(normName) ||
      normalizedList.includes(normShort) ||
      canonical === normName
    ) {
      for (const item of normalizedList) {
        if (item) aliases.add(item);
      }
    }
  }

  return Array.from(aliases).sort((a, b) => b.length - a.length);
}

function hasWholePhrase(haystack: string, phrase: string): boolean {
  if (!phrase) return false;
  const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?:^|\\s)${escaped}(?:\\s|$)`).test(haystack);
}

interface MatchQualifiers {
  isWomen: boolean;
  isATeam: boolean;
  isU19: boolean;
}

function detectQualifiers(text: string): MatchQualifiers {
  const norm = normalizeMatchText(text);
  const isWomen =
    /\bwomen\b|\bwomens\b|\bwpl\b/.test(norm) ||
    /\b[a-z]{2,4} w\b/.test(norm) ||
    /\b[a-z]{2,4}w\b/.test(norm);
  const isATeam = /\b[a-z]{2,4} a\b/.test(norm);
  const isU19 = /\bu19\b|\bunder 19\b/.test(norm);
  return { isWomen, isATeam, isU19 };
}

function extractStageToken(text: string): string | null {
  const norm = normalizeMatchText(text);
  const matchNumOrdinal = norm.match(/\b(\d+)(?:st|nd|rd|th)\s+match\b/);
  if (matchNumOrdinal) return `m:${matchNumOrdinal[1]}`;
  const matchNum = norm.match(/\b(?:match|m)\s*(\d+)\b/);
  if (matchNum) return `m:${matchNum[1]}`;
  const ordinalMatch = norm.match(/\b(\d+)(?:st|nd|rd|th)\b/);
  if (ordinalMatch) return `ord:${ordinalMatch[1]}`;
  if (/\bqualifier 1\b|\bq1\b/.test(norm)) return 'stage:q1';
  if (/\bqualifier 2\b|\bq2\b/.test(norm)) return 'stage:q2';
  if (/\beliminator\b/.test(norm)) return 'stage:eliminator';
  if (/\bsemi final\b|\bsf\d?\b/.test(norm)) return 'stage:sf';
  if (/\bfinal\b/.test(norm)) return 'stage:final';
  return null;
}

function extractHighlightSignalScore(normTitle: string): number {
  if (/\bhighlights?\b/.test(normTitle)) return 25;
  if (/\bextended highlights\b/.test(normTitle)) return 25;
  return 0;
}

/**
 * Parse a relative YouTube publish string (e.g., "2 days ago", "1 year ago",
 * "Streamed 3 months ago", "1d ago") into an approximate [minDaysAgo, maxDaysAgo] range.
 */
export function parseRelativeAgeRangeDays(
  publishedText: string | null | undefined,
): [number, number] | null {
  if (!publishedText) return null;
  const text = publishedText.toLowerCase().trim();
  const match = text.match(
    /(\d+)\s*(second|sec|s|minute|min|m|hour|hr|h|day|d|week|wk|w|month|mo|year|yr|y)s?\s*ago/,
  );
  if (!match) return null;

  const value = Number.parseInt(match[1], 10);
  if (Number.isNaN(value) || value < 0) return null;

  const unit = match[2];
  if (unit === 'second' || unit === 'sec' || unit === 's' || unit === 'minute' || unit === 'min' || unit === 'm') {
    return [0, 1];
  }
  if (unit === 'hour' || unit === 'hr' || unit === 'h') {
    return [0, Math.max(1, value / 24 + 0.5)];
  }
  if (unit === 'day' || unit === 'd') {
    return [Math.max(0, value - 0.5), value + 1.5];
  }
  if (unit === 'week' || unit === 'wk' || unit === 'w') {
    return [Math.max(0, value * 7 - 2), value * 7 + 6];
  }
  if (unit === 'month' || unit === 'mo') {
    return [Math.max(0, value * 28 - 5), value * 32 + 10];
  }
  if (unit === 'year' || unit === 'yr' || unit === 'y') {
    return [Math.max(0, value * 365 - 30), value * 366 + 60];
  }
  return null;
}

/**
 * Score a video candidate against a cricket match.
 * Returns null if the candidate is clearly not for this match.
 */
export function scoreHighlightMatch(
  match: MatchCandidateContext,
  video: VideoCandidateInput,
): number | null {
  const normTitle = normalizeMatchText(video.title);
  if (!normTitle) return null;

  // Reject non-highlight content (interviews, previews, video games, etc.)
  if (NON_HIGHLIGHT_KEYWORDS.some((kw) => normTitle.includes(kw))) {
    return null;
  }

  // Disambiguate Women's / A-team / U19 matches strictly
  const matchContextText = `${match.team1Name} ${match.team1Short} ${match.team2Name} ${match.team2Short} ${match.series} ${match.matchDesc}`;
  const matchQual = detectQualifiers(matchContextText);
  const videoQual = detectQualifiers(video.title);

  if (matchQual.isWomen !== videoQual.isWomen) return null;
  if (matchQual.isATeam !== videoQual.isATeam) return null;
  if (matchQual.isU19 !== videoQual.isU19) return null;

  // Both teams must appear in the video title
  const team1Aliases = getTeamAliases(match.team1Name, match.team1Short);
  const team2Aliases = getTeamAliases(match.team2Name, match.team2Short);

  const matchedTeam1 = team1Aliases.some((alias) => hasWholePhrase(normTitle, alias));
  const matchedTeam2 = team2Aliases.some((alias) => hasWholePhrase(normTitle, alias));
  if (!matchedTeam1 || !matchedTeam2) {
    return null;
  }

  // Explicit year guard: if the video title names tournament year(s) like "2025",
  // reject if none match the match's year (allowing YYYY-YY season overlap in Jan-Apr).
  if (match.startTimestamp) {
    const startDate = new Date(match.startTimestamp);
    const matchYear = startDate.getUTCFullYear();
    const matchMonth = startDate.getUTCMonth(); // 0-indexed
    const titleYears = Array.from(normTitle.matchAll(/\b(20[1-3]\d)\b/g)).map((m) =>
      Number.parseInt(m[1], 10),
    );
    if (titleYears.length > 0) {
      const seasonAllowed = matchMonth <= 3 ? [matchYear, matchYear - 1] : [matchYear];
      if (!titleYears.some((y) => seasonAllowed.includes(y))) {
        return null;
      }
    }
  }

  // Date window check when ISO timestamp exists (BCCI / IPL)
  if (match.startTimestamp && video.publishedAt) {
    const pubMs = Date.parse(video.publishedAt);
    if (!Number.isNaN(pubMs)) {
      const diffDays = (pubMs - match.startTimestamp) / (1000 * 60 * 60 * 24);
      const maxAfterDays = match.format === 'Test' ? 8 : 4;
      if (diffDays < -1 || diffDays > maxAfterDays) {
        return null;
      }
    }
  }

  // Relative publish-age check (YouTube search returns e.g. "2 days ago", "1 year ago")
  if (match.startTimestamp && video.publishedText) {
    const ageRange = parseRelativeAgeRangeDays(video.publishedText);
    if (ageRange) {
      const [minAgeDays, maxAgeDays] = ageRange;
      const matchAgeDays = Math.max(0, (Date.now() - match.startTimestamp) / (1000 * 60 * 60 * 24));
      const maxAfterGrace = match.format === 'Test' ? 10 : 6;
      if (minAgeDays > matchAgeDays + 5 || maxAgeDays < matchAgeDays - maxAfterGrace) {
        return null;
      }
    }
  }

  // Stage / ordinal disambiguation (e.g., 1st ODI vs 2nd ODI, Q1 vs Q2)
  let score = 60;
  const matchStage = extractStageToken(match.matchDesc);
  const videoStage = extractStageToken(video.title);
  if (matchStage && videoStage) {
    if (matchStage !== videoStage) {
      return null;
    }
    score += 25;
  }

  // Format signal
  const formatToken = normalizeMatchText(match.format);
  if (formatToken && formatToken !== 'other' && hasWholePhrase(normTitle, formatToken)) {
    score += 10;
  }

  // Explicit "highlights" keyword boost
  score += extractHighlightSignalScore(normTitle);

  // Series / year overlap bonus
  if (match.startTimestamp) {
    const year = String(new Date(match.startTimestamp).getUTCFullYear());
    if (normTitle.includes(year)) score += 5;
  }

  return score;
}

/**
 * Determine whether a match is an IPL match.
 */
export function isIplMatch(series: string): boolean {
  return /\b(ipl|indian premier league)\b/i.test(series);
}

/**
 * Determine whether a match is likely hosted by BCCI (India home or Indian domestic).
 * Note: India away tours are NOT hosted by BCCI, so callers should fall back to YouTube
 * if BCCI returns no matching highlights.
 */
export function isBcciCandidateMatch(match: MatchCandidateContext): boolean {
  const combinedTeams = `${match.team1Name} ${match.team1Short} ${match.team2Name} ${match.team2Short}`;
  const hasIndiaTeam = /\bindia\b|\bind\b/i.test(normalizeMatchText(combinedTeams));
  const isIndianDomestic =
    /\b(ranji|duleep|vijay hazare|syed mushtaq|irani|deodhar|challenger|ck nayudu|cooch behar|colonel|wpl|women s premier)\b/i.test(
      normalizeMatchText(match.series),
    );
  return hasIndiaTeam || isIndianDomestic;
}
