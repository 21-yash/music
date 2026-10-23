import { logger } from '../utils/logger';

export interface LyricLine {
  timeMs: number;
  text: string;
}

export interface LyricsResult {
  isSynced: boolean;
  lines: LyricLine[];
  plainLyrics: string;
  source: 'lrclib' | 'jiosaavn' | 'none';
}

/** Result plus a hint for the cache layer: false = don't cache (a fetch failed/timed out). */
export interface LyricsOutcome {
  result: LyricsResult;
  cacheable: boolean;
}

const LRCLIB_USER_AGENT = 'MusicSportsApp/1.0 (https://github.com/21-yash/music)';
const LRCLIB_API = 'https://lrclib.net/api';

// ─── Tunables ────────────────────────────────────────────────────────────────
const SYNCED_MAX_DIFF_S = 2;       // synced lyrics: duration must be within ±2s
const SYNCED_END_SLACK_S = 3;      // last timestamp may exceed song length by at most this
const PLAIN_MAX_DIFF_S = 8;        // plain lyrics: duration within ±8s
const COMPLETENESS_RATIO = 0.8;    // "complete enough" = >= 80% of the fullest candidate's lines
const MAX_ARTISTS = 4;
const MAX_FIELD_LEN = 300;
const REQUEST_TIMEOUT_MS = 4500;
const OVERALL_DEADLINE_MS = 8000;  // one deadline for all LRCLIB calls of a lookup
const FALLBACK_TIMEOUT_MS = 3000;  // JioSaavn/YouTube plain getter
const FOREIGN_MAX_RATIO = 0.25;    // > 25% of lines in a script other than Latin/Devanagari => "foreign"

interface LrclibResponse {
  id?: number;
  name?: string;
  trackName?: string;
  artistName?: string;
  albumName?: string;
  duration?: number;
  instrumental?: boolean;
  plainLyrics?: string | null;
  syncedLyrics?: string | null;
}

// ─── Artist splitting ────────────────────────────────────────────────────────

// Keyword splitters now require a word boundary on BOTH sides, so
// "featuring" no longer becomes "uring" and "Bill Withers" is no longer cut.
const ARTIST_SPLIT = /[,;&\/]|\b(?:featuring|feat|ft|with)\b\.?/i;

/**
 * Split artist string into individual clean artist names.
 * Supports comma, semicolon, ampersand, slash, and feat/ft keywords.
 */
export function extractArtistNames(artistStr?: string): string[] {
  if (!artistStr || !artistStr.trim()) return [];
  const raw = artistStr.split(ARTIST_SPLIT);
  const result: string[] = [];
  for (const part of raw) {
    const cleaned = part.replace(/^[.\s]+|[.\s]+$/g, '').trim();
    if (cleaned.length > 0 && !/^(feat|ft|featuring|with)$/i.test(cleaned)) {
      if (!result.some(existing => existing.toLowerCase() === cleaned.toLowerCase())) {
        result.push(cleaned);
      }
    }
  }
  return result;
}

// ─── LRC parsing (unchanged) ─────────────────────────────────────────────────

/**
 * Parse standard LRC format string into structured LyricLine array.
 * Example input:
 * [00:12.34] Line one
 * [00:16.80] Line two
 */
export function parseLrc(lrcText: string): LyricLine[] {
  if (!lrcText) return [];

  const lines = lrcText.split('\n');
  const result: LyricLine[] = [];
  const timeRegex = /\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g;

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) continue;

    // Find all timestamps on this line
    const matches = Array.from(trimmed.matchAll(timeRegex));
    if (matches.length === 0) continue;

    // Text is everything after the last timestamp
    const text = trimmed.replace(timeRegex, '').trim();

    for (const match of matches) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseInt(match[2], 10);
      const millisPart = match[3] || '0';
      const millis = parseInt(millisPart.padEnd(3, '0').slice(0, 3), 10);

      const timeMs = minutes * 60 * 1000 + seconds * 1000 + millis;
      result.push({ timeMs, text });
    }
  }

  // Sort chronologically
  return result.sort((a, b) => a.timeMs - b.timeMs);
}

// ─── HTTP ────────────────────────────────────────────────────────────────────

interface FetchResult<T> {
  status: 'ok' | 'notFound' | 'error';
  data: T | null;
}

async function fetchFromLrclib<T>(
  url: string,
  deadline?: AbortSignal,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<FetchResult<T>> {
  if (deadline?.aborted) return { status: 'error', data: null };

  const controller = new AbortController();
  const onDeadline = () => controller.abort();
  deadline?.addEventListener('abort', onDeadline, { once: true });
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': LRCLIB_USER_AGENT, Accept: 'application/json' },
      signal: controller.signal,
    });
    if (res.status === 404) return { status: 'notFound', data: null };
    if (!res.ok) return { status: 'error', data: null };
    return { status: 'ok', data: (await res.json()) as T };
  } catch {
    return { status: 'error', data: null };
  } finally {
    clearTimeout(timeoutId);
    deadline?.removeEventListener('abort', onDeadline);
  }
}

function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms);
    p.then(
      v => { clearTimeout(t); resolve(v); },
      e => { clearTimeout(t); reject(e); },
    );
  });
}

// ─── Comparison helpers (used ONLY for comparing, never for building queries) ─

const hasText = (s?: string | null): s is string => typeof s === 'string' && s.trim().length > 0;

function normalize(s?: string | null): string {
  return (s ?? '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')          // Latin diacritics
    .replace(/[^\p{L}\p{N}\p{M}]+/gu, ' ')    // keep letters/digits/marks of ALL scripts
    .trim();
}

const DESCRIPTORS = /\b(official|video|audio|lyrical|lyric|lyrics|full song|visualizer|hd|4k)\b/g;

interface TitleKeys { full: string; core: string; }

function titleKeys(raw?: string | null): TitleKeys {
  const lower = (raw ?? '').toLowerCase();
  const core = normalize(
    lower
      .replace(/\([^)]*\)|\[[^\]]*\]/g, ' ')        // (From "Movie"), [Remix] ...
      .replace(/\s[-–—]\s*from\b.*$/, ' ')           // "- From Movie"
      .replace(DESCRIPTORS, ' '),
  );
  return { full: normalize(lower), core };
}

function keysMatch(a: TitleKeys, b: TitleKeys): boolean {
  return (!!a.full && a.full === b.full) || (!!a.core && a.core === b.core);
}

// Version markers are read from the RAW strings (before brackets are stripped),
// and the candidate's set must equal the target's set.
const MARKER_RE =
  /\b(?:remix|reprise|live|unplugged|acoustic|lo-?fi|slowed|reverb|sped up|speed up|nightcore|8d|karaoke|instrumental|cover|mashup|rework|version|edit|extended|mix)\b/g;

function markersOf(raw?: string | null): Set<string> {
  const found = (raw ?? '').toLowerCase().match(MARKER_RE) ?? [];
  return new Set(found.map(m => m.replace('-', '')));
}

function sameSet(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}

function artistTokens(s?: string | null): string[] {
  const n = normalize(s);
  return n ? n.split(' ') : [];
}

/** Token-based, bidirectional, word-boundary artist match. */
function artistMatches(candArtistName: string | undefined, targetArtists: string[]): boolean {
  const candAll = new Set(artistTokens(candArtistName));
  if (candAll.size === 0) return false;
  const candIndividuals = extractArtistNames(candArtistName)
    .map(artistTokens)
    .filter(t => t.length > 0);

  for (const t of targetArtists) {
    const tt = artistTokens(t);
    if (tt.length === 0) continue;
    // target artist fully inside the candidate's credit
    if (tt.every(tok => candAll.has(tok))) return true;
    // a candidate credit fully inside the target name ("Arijit" vs "Arijit Singh")
    if (candIndividuals.some(ci => ci.every(tok => tt.includes(tok)))) return true;
  }
  return false;
}

// ─── Candidate evaluation ────────────────────────────────────────────────────

interface MatchContext {
  title: TitleKeys;
  titleMarkers: Set<string>;
  artists: string[];
  durationSeconds: number;      // 0 = unknown
  directIds: Set<number>;       // ids returned by /api/get with artist_name (artist already confirmed by LRCLIB)
}

type Identity = { ok: true; path: 'track' | 'album' } | { ok: false; reason: string };

function checkIdentity(c: LrclibResponse, ctx: MatchContext): Identity {
  if (c.instrumental) return { ok: false, reason: 'instrumental' };

  const trackName = c.trackName || c.name || '';
  let path: 'track' | 'album';
  let candMarkers = markersOf(trackName);

  if (keysMatch(titleKeys(trackName), ctx.title)) {
    path = 'track';
  } else if (keysMatch(titleKeys(c.albumName), ctx.title)) {
    // Title-as-album is ONLY a fallback for when the track name did not match
    path = 'album';
    for (const m of markersOf(c.albumName)) candMarkers.add(m);
  } else {
    return { ok: false, reason: 'title' };
  }

  if (!sameSet(candMarkers, ctx.titleMarkers)) return { ok: false, reason: 'version-marker' };

  // Both paths require an artist match (skipped only if the app sent no artist at all)
  if (ctx.artists.length > 0) {
    const confirmed = c.id !== undefined && ctx.directIds.has(c.id);
    if (!confirmed && !artistMatches(c.artistName, ctx.artists)) return { ok: false, reason: 'artist' };
  }
  return { ok: true, path };
}

interface Evaluated {
  id: number;
  item: LrclibResponse;
  diff: number | null;
  lines: LyricLine[];
  plain: string;
  lineCount: number;
  maxGapMs: number;
  foreign: boolean;             // mostly written in a script other than Latin/Devanagari
}
interface Rejection { id: number; reason: string; diff: number | null; }
interface PoolEvaluation { synced: Evaluated[]; plain: Evaluated[]; rejected: Rejection[]; }

const countLines = (text: string) => text.split('\n').filter(l => l.trim().length > 0).length;

/**
 * Share of lyric lines (0..1) written mostly in a script other than Latin/Devanagari.
 * Classified per LINE, not per letter: vowel signs (matras) are marks, so letter counts
 * would under-weight Gurmukhi/Devanagari lines compared to their Roman versions.
 * Letters AND marks are counted on both sides so the comparison stays consistent.
 */
function foreignRatio(text: string): number {
  let total = 0;
  let foreign = 0;
  for (const line of text.split('\n')) {
    const all = (line.match(/[\p{L}\p{M}]/gu) ?? []).length;
    if (all === 0) continue;                          // skip empty / punctuation-only lines
    total++;
    const known = (line.match(/(?=[\p{L}\p{M}])[\p{Script=Latin}\p{Script=Devanagari}]/gu) ?? []).length;
    if (known < all / 2) foreign++;
  }
  return total ? foreign / total : 0;
}

function maxGap(lines: LyricLine[]): number {
  let max = 0;
  for (let i = 1; i < lines.length; i++) max = Math.max(max, lines[i].timeMs - lines[i - 1].timeMs);
  return max;
}

function evaluatePool(pool: Map<number, LrclibResponse>, ctx: MatchContext): PoolEvaluation {
  const synced: Evaluated[] = [];
  const plain: Evaluated[] = [];
  const rejected: Rejection[] = [];

  for (const [id, c] of pool) {
    const identity = checkIdentity(c, ctx);
    if (!identity.ok) {
      rejected.push({ id, reason: identity.reason, diff: null });
      continue;
    }

    const candDuration = typeof c.duration === 'number' && c.duration > 0 ? c.duration : 0;
    const diff = ctx.durationSeconds > 0 && candDuration > 0
      ? Math.abs(candDuration - ctx.durationSeconds)
      : null;
    const reasons: string[] = [];

    // ── Synced gate: known durations on both sides, within ±2s, timing plausible
    const syncedText = hasText(c.syncedLyrics) ? c.syncedLyrics : '';
    let parsed: LyricLine[] = [];
    let syncedOk = false;
    if (syncedText) {
      if (diff === null) {
        reasons.push('synced:duration-unknown');
      } else if (diff > SYNCED_MAX_DIFF_S) {
        reasons.push(`synced:duration(${diff.toFixed(1)}s)`);
      } else {
        parsed = parseLrc(syncedText);
        const last = parsed.length ? parsed[parsed.length - 1].timeMs : 0;
        if (!parsed.some(l => l.text.trim().length > 0)) reasons.push('synced:no-lines');
        else if (last > (ctx.durationSeconds + SYNCED_END_SLACK_S) * 1000) reasons.push('synced:overruns-duration');
        else syncedOk = true;
      }
    }

    // ── Plain gate: LRCLIB's own plainLyrics field (no tag stripping needed)
    let plainText = hasText(c.plainLyrics) ? c.plainLyrics : '';
    if (!plainText && syncedText) {
      // Rare: no plainLyrics field. Build it from the parsed line texts.
      if (parsed.length === 0) parsed = parseLrc(syncedText);
      plainText = parsed.map(l => l.text).join('\n').trim();
    }
    const plainOk =
      !!plainText && (ctx.durationSeconds <= 0 || diff === null || diff <= PLAIN_MAX_DIFF_S);
    if (plainText && !plainOk) reasons.push(`plain:duration(${diff}s)`);

    const foreign = !!plainText && foreignRatio(plainText) > FOREIGN_MAX_RATIO;

    if (syncedOk) {
      synced.push({
        id, item: c, diff, lines: parsed, plain: plainText,
        lineCount: parsed.filter(l => l.text.trim().length > 0).length,
        maxGapMs: maxGap(parsed),
        foreign,
      });
    }
    if (plainOk) {
      plain.push({ id, item: c, diff, lines: [], plain: plainText, lineCount: countLines(plainText), maxGapMs: 0, foreign });
    }
    if (!syncedOk && !plainOk) {
      rejected.push({ id, reason: reasons.join(',') || 'no-lyrics', diff });
    }
  }
  return { synced, plain, rejected };
}

/**
 * Among already-verified candidates: complete submissions first, then closest
 * duration, then more lines, then smaller max timestamp gap, then lowest id.
 */
function rank(list: Evaluated[]): Evaluated[] {
  if (list.length === 0) return list;
  const best = Math.max(...list.map(e => e.lineCount));
  const solidMin = Math.ceil(best * COMPLETENESS_RATIO);
  return [...list].sort((a, b) => {
    const aSolid = a.lineCount >= solidMin;
    const bSolid = b.lineCount >= solidMin;
    if (aSolid !== bSolid) return aSolid ? -1 : 1;
    // Among complete submissions, prefer Latin/Devanagari over other scripts (soft preference, not a filter)
    if (a.foreign !== b.foreign) return a.foreign ? 1 : -1;
    const ad = a.diff ?? Infinity;
    const bd = b.diff ?? Infinity;
    if (ad !== bd) return ad - bd;
    if (a.lineCount !== b.lineCount) return b.lineCount - a.lineCount;
    if (a.maxGapMs !== b.maxGapMs) return a.maxGapMs - b.maxGapMs;
    return a.id - b.id;
  });
}

const emptyResult = (): LyricsResult => ({ isSynced: false, lines: [], plainLyrics: '', source: 'none' });

// ─── Main entry ──────────────────────────────────────────────────────────────

/**
 * Same behaviour as getLyrics, but also tells the caller whether the result is
 * safe to cache (false when any network call failed or timed out).
 *
 * Order: synced (LRCLIB) -> plain (provider, exact by songId) -> plain (LRCLIB) -> none
 */
export async function getLyricsWithMeta(
  title: string,
  artist: string,
  durationSeconds?: number,
  fallbackPlainGetter?: () => Promise<string | null>,
): Promise<LyricsOutcome> {
  const rawTitle = (title ?? '').trim().slice(0, MAX_FIELD_LEN);   // raw title, only length-capped
  if (!rawTitle) return { result: emptyResult(), cacheable: false };

  const artists = extractArtistNames((artist ?? '').slice(0, MAX_FIELD_LEN)).slice(0, MAX_ARTISTS);
  const targetDuration = durationSeconds && durationSeconds > 0 ? durationSeconds : 0;

  const ctx: MatchContext = {
    title: titleKeys(rawTitle),
    titleMarkers: markersOf(rawTitle),
    artists,
    durationSeconds: targetDuration,
    directIds: new Set<number>(),
  };

  const pool = new Map<number, LrclibResponse>();
  let hadError = false;
  let stage = 0;
  let evald: PoolEvaluation = { synced: [], plain: [], rejected: [] };

  const deadline = new AbortController();
  const deadlineTimer = setTimeout(() => deadline.abort(), OVERALL_DEADLINE_MS);

  const addSingle = (r: FetchResult<LrclibResponse>, direct: boolean): void => {
    if (r.status === 'error') hadError = true;
    const c = r.data;
    if (c && typeof c.id === 'number') {
      pool.set(c.id, c);
      if (direct) ctx.directIds.add(c.id);
    }
  };
  const addList = (r: FetchResult<LrclibResponse[]>): void => {
    if (r.status === 'error') hadError = true;
    if (Array.isArray(r.data)) {
      for (const it of r.data) if (it && typeof it.id === 'number') pool.set(it.id, it);
    }
  };

  const getUrl = (artistName: string) => {
    const p = new URLSearchParams({ track_name: rawTitle, artist_name: artistName });
    if (targetDuration > 0) p.set('duration', Math.round(targetDuration).toString());
    return `${LRCLIB_API}/get?${p.toString()}`;
  };
  const searchUrl = (artistName?: string) => {
    const p = new URLSearchParams({ track_name: rawTitle });
    if (artistName) p.set('artist_name', artistName);
    return `${LRCLIB_API}/search?${p.toString()}`;
  };

  const getLookup = (artistName: string) =>
    fetchFromLrclib<LrclibResponse>(getUrl(artistName), deadline.signal)
      .then(r => addSingle(r, true));

  const searchLookup = (artistName?: string) =>
    fetchFromLrclib<LrclibResponse[]>(searchUrl(artistName), deadline.signal)
      .then(addList);

  const qSearch = () =>
    fetchFromLrclib<LrclibResponse[]>(
      `${LRCLIB_API}/search?q=${encodeURIComponent(rawTitle)}`, deadline.signal,
    ).then(addList);

  const needSynced = () => evald.synced.length === 0 && !deadline.signal.aborted;

  try {
    // 1) /api/get + /api/search for the primary artist, IN PARALLEL. /api/get is only a
    // candidate (it can return a sparse upload), so the search results must land in the
    // pool alongside it and rank() picks the best. (No artist => track-only search.)
    stage = 1;
    await Promise.all(
      artists[0]
        ? [getLookup(artists[0]), searchLookup(artists[0])]
        : [searchLookup()],
    );
    evald = evaluatePool(pool, ctx);

    // 2) track-only search: catches other credited artists / odd artist spelling
    if (needSynced() && artists.length > 0) {
      stage = 2;
      await searchLookup();
      evald = evaluatePool(pool, ctx);
    }

    // 3) last resort: title stored as album (q=). Runs whenever there is still no synced match.
    if (needSynced()) {
      stage = 3;
      await qSearch();
      evald = evaluatePool(pool, ctx);
    }
  } catch (err) {
    hadError = true;
    logger.warn({ err, title, artist }, 'LRCLIB lookup failed unexpectedly');
  } finally {
    clearTimeout(deadlineTimer);
  }

  const bestSynced = rank(evald.synced)[0];
  const bestPlain = rank(evald.plain)[0];

  logger.info(
    {
      title, artist, durationSeconds, stage, poolSize: pool.size,
      synced: evald.synced.length, plain: evald.plain.length,
      picked: bestSynced
        ? { kind: 'synced', id: bestSynced.id, diff: bestSynced.diff, lines: bestSynced.lineCount, foreign: bestSynced.foreign }
        : bestPlain ? { kind: 'plain-lrclib-candidate', id: bestPlain.id, diff: bestPlain.diff } : null,
      rejected: evald.rejected.slice(0, 15),
    },
    'Lyrics lookup',
  );

  // 1) Synced lyrics from LRCLIB
  if (bestSynced) {
    return {
      result: { isSynced: true, lines: bestSynced.lines, plainLyrics: '', source: 'lrclib' },
      cacheable: true,
    };
  }

  // 2) Provider plain lyrics (exact by songId)
  let fallbackFailed = false;
  if (fallbackPlainGetter) {
    try {
      const plain = await withTimeout(fallbackPlainGetter(), FALLBACK_TIMEOUT_MS);
      if (plain && plain.trim().length > 0) {
        return {
          result: { isSynced: false, lines: [], plainLyrics: plain, source: 'jiosaavn' },
          cacheable: !hadError,
        };
      }
    } catch (err) {
      fallbackFailed = true;
      logger.warn({ err }, 'Fallback plain lyrics getter failed');
    }
  }

  // 3) Verified LRCLIB plain lyrics
  if (bestPlain) {
    return {
      result: { isSynced: false, lines: [], plainLyrics: bestPlain.plain, source: 'lrclib' },
      cacheable: !hadError,
    };
  }

  // 4) Nothing
  return { result: emptyResult(), cacheable: !hadError && !fallbackFailed };
}

/** Backwards-compatible wrapper: same signature and return type as before. */
export async function getLyrics(
  title: string,
  artist: string,
  durationSeconds?: number,
  fallbackPlainGetter?: () => Promise<string | null>,
): Promise<LyricsResult> {
  return (await getLyricsWithMeta(title, artist, durationSeconds, fallbackPlainGetter)).result;
}