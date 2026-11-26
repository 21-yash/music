import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  scoreHighlightMatch,
  parseDurationToSeconds,
  parseRelativeAgeRangeDays,
  isIplMatch,
  isBcciCandidateMatch,
  type MatchCandidateContext,
} from '../../src/providers/sports/highlights/highlightMatcher';
import { parseYouTubeInitialData, OFFICIAL_CRICKET_CHANNELS } from '../../src/providers/sports/highlights/YouTubeHighlightsProvider';
import { getMatchHighlights, type HighlightsProviderSet } from '../../src/services/highlightsService';
import * as sportsService from '../../src/services/sportsService';

vi.mock('../../src/config/redis', () => ({
  getRedisClient: () => {
    throw new Error('Redis disabled in unit test');
  },
}));

describe('Highlight Matcher', () => {
  const indVsZimWomen: MatchCandidateContext = {
    team1Name: 'India Women',
    team1Short: 'INDW',
    team2Name: 'Zimbabwe Women',
    team2Short: 'ZIMW',
    series: 'Zimbabwe Women tour of India, 2026',
    matchDesc: '3rd ODI',
    format: 'ODI',
    startTimestamp: Date.parse('2026-10-28T04:00:00.000Z'),
  };

  it('matches BCCI women ODI highlight and rejects men ODI highlight', () => {
    const womenScore = scoreHighlightMatch(indVsZimWomen, {
      title: 'IND-W vs ZIM-W 2026, 3rd ODI: Match Highlights',
      publishedAt: '2026-10-28T11:35:00.000Z',
      durationSeconds: 649,
    });
    expect(womenScore).not.toBeNull();
    expect(womenScore!).toBeGreaterThan(80);

    const menTitleForMenMatch: MatchCandidateContext = {
      ...indVsZimWomen,
      team1Name: 'India',
      team1Short: 'IND',
      team2Name: 'Zimbabwe',
      team2Short: 'ZIM',
      series: 'Zimbabwe tour of India, 2026',
    };
    const crossGenderScore = scoreHighlightMatch(menTitleForMenMatch, {
      title: 'IND-W vs ZIM-W 2026, 3rd ODI: Match Highlights',
      publishedAt: '2026-10-28T11:35:00.000Z',
      durationSeconds: 649,
    });
    expect(crossGenderScore).toBeNull();
  });

  it('rejects mismatched match ordinals and non-highlight videos', () => {
    const wrongOrdinal = scoreHighlightMatch(indVsZimWomen, {
      title: 'IND-W vs ZIM-W 2026, 2nd ODI: Match Highlights',
      publishedAt: '2026-10-28T11:35:00.000Z',
      durationSeconds: 600,
    });
    expect(wrongOrdinal).toBeNull();

    const pressConf = scoreHighlightMatch(indVsZimWomen, {
      title: 'IND-W vs ZIM-W 2026, 3rd ODI: Post-match Press Conference',
      publishedAt: '2026-10-28T12:00:00.000Z',
      durationSeconds: 400,
    });
    expect(pressConf).toBeNull();
  });

  it('matches IPL match numbers and playoff stages', () => {
    const iplQ2: MatchCandidateContext = {
      team1Name: 'Gujarat Titans',
      team1Short: 'GT',
      team2Name: 'Rajasthan Royals',
      team2Short: 'RR',
      series: 'Indian Premier League 2026',
      matchDesc: 'Qualifier 2',
      format: 'T20',
      startTimestamp: Date.parse('2026-05-29T14:00:00.000Z'),
    };
    expect(isIplMatch(iplQ2.series)).toBe(true);
    expect(
      scoreHighlightMatch(iplQ2, {
        title: 'IPL 2026 Q2: GT vs RR - Match Highlights',
        publishedAt: '2026-05-29T16:17:35.000Z',
        durationSeconds: 802,
      }),
    ).not.toBeNull();

    const iplM14: MatchCandidateContext = {
      ...iplQ2,
      matchDesc: '14th Match',
    };
    expect(
      scoreHighlightMatch(iplM14, {
        title: 'IPL 2026 M14: GT vs RR - Match Highlights',
        publishedAt: '2026-05-29T16:17:35.000Z',
        durationSeconds: 802,
      }),
    ).not.toBeNull();
  });

  it('rejects highlights outside valid date window or from prior years', () => {
    const staleVideo = scoreHighlightMatch(indVsZimWomen, {
      title: 'IND-W vs ZIM-W 2024, 3rd ODI: Match Highlights',
      publishedAt: '2024-10-28T11:35:00.000Z',
      durationSeconds: 600,
    });
    expect(staleVideo).toBeNull();

    // Recent AFG A vs HK match must reject 2025/2-years-ago YouTube videos
    const recentAfgHk: MatchCandidateContext = {
      team1Name: 'Hong Kong, China',
      team1Short: 'HKC',
      team2Name: 'Afghanistan A',
      team2Short: 'AFGA',
      series: "ACC Men's Asia Cup Rising Stars 2026",
      matchDesc: '9th Match, Group A',
      format: 'ODI',
      startTimestamp: Date.now() - 3 * 24 * 60 * 60 * 1000,
    };
    expect(
      scoreHighlightMatch(recentAfgHk, {
        title: "Highlights | Match 9| Afghanistan 'A' v Hong Kong, China | DP World Men's Asia Cup Rising Stars 2026",
        publishedAt: null,
        publishedText: '2 days ago',
        durationSeconds: 1480,
      }),
    ).not.toBeNull();

    expect(
      scoreHighlightMatch(recentAfgHk, {
        title: 'Afghanistan A vs Hong Kong | Group Stage | Highlights | DP World Asia Cup Rising Stars 2025',
        publishedAt: null,
        publishedText: '1 year ago',
        durationSeconds: 1097,
      }),
    ).toBeNull();

    expect(
      scoreHighlightMatch(recentAfgHk, {
        title: 'Afghanistan A vs Hong Kong | Highlights',
        publishedAt: null,
        publishedText: '2 years ago',
        durationSeconds: 900,
      }),
    ).toBeNull();

    expect(parseRelativeAgeRangeDays('2 days ago')).toEqual([1.5, 3.5]);
  });

  it('parses duration strings accurately', () => {
    expect(parseDurationToSeconds('10:49')).toBe(649);
    expect(parseDurationToSeconds('1:02:05')).toBe(3725);
    expect(parseDurationToSeconds('')).toBe(0);
  });

  it('detects BCCI candidates for India and domestic series', () => {
    expect(isBcciCandidateMatch(indVsZimWomen)).toBe(true);
    expect(
      isBcciCandidateMatch({
        ...indVsZimWomen,
        team1Name: 'Tamil Nadu',
        team1Short: 'TN',
        team2Name: 'Railways',
        team2Short: 'RSPB',
        series: 'Ranji Trophy Elite 2026-27',
      }),
    ).toBe(true);
    expect(
      isBcciCandidateMatch({
        ...indVsZimWomen,
        team1Name: 'Australia',
        team1Short: 'AUS',
        team2Name: 'England',
        team2Short: 'ENG',
        series: 'England tour of Australia, 2026-27',
      }),
    ).toBe(false);
  });
});

describe('YouTube ytInitialData parser', () => {
  it('extracts videoRenderer items cleanly', () => {
    const fakeHtml = `<script>var ytInitialData = ${JSON.stringify({
      contents: {
        twoColumnSearchResultsRenderer: {
          primaryContents: {
            sectionListRenderer: {
              contents: [
                {
                  itemSectionRenderer: {
                    contents: [
                      {
                        videoRenderer: {
                          videoId: 'U5R6tpHSyLk',
                          title: { runs: [{ text: 'Australia v England 2026-27 | Second T20I' }] },
                          ownerText: {
                            runs: [
                              {
                                text: 'cricket.com.au',
                                navigationEndpoint: { browseEndpoint: { browseId: 'UCkBY0aHJP9BwjZLDYxAQrKg' } },
                              },
                            ],
                          },
                          lengthText: { simpleText: '8:08' },
                          publishedTimeText: { simpleText: '1d ago' },
                          thumbnail: { thumbnails: [{ url: 'https://i.ytimg.com/vi/U5R6tpHSyLk/hqdefault.jpg' }] },
                        },
                      },
                    ],
                  },
                },
              ],
            },
          },
        },
      },
    })};</script>`;

    const videos = parseYouTubeInitialData(fakeHtml);
    expect(videos).toHaveLength(1);
    expect(videos[0]).toMatchObject({
      videoId: 'U5R6tpHSyLk',
      channelName: 'cricket.com.au',
      channelId: 'UCkBY0aHJP9BwjZLDYxAQrKg',
      durationSeconds: 488,
    });
    expect(OFFICIAL_CRICKET_CHANNELS.get('UC7ITT3ooYWDYY_ehIUbt6eg')).toBe('Asian Cricket Council');
  });
});

describe('Highlights Service routing', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const makeMatch = (overrides: Partial<any> = {}) => ({
    match: {
      id: '101',
      slug: 'ind-vs-aus',
      series: 'Australia tour of India 2026',
      seriesId: '1',
      matchDesc: '1st T20I',
      format: 'T20I',
      team1: { id: '1', name: 'India', shortName: 'IND', flagUrl: null },
      team2: { id: '2', name: 'Australia', shortName: 'AUS', flagUrl: null },
      team1Score: '180/4',
      team2Score: '170/8',
      venue: 'Mumbai',
      status: 'India won by 10 runs',
      state: 'completed',
      isLive: false,
      startTimestamp: Date.parse('2026-10-20T13:30:00.000Z'),
      endTimestamp: null,
      playerOfMatch: null,
      liveScore: null,
      currentBatsmen: [],
      currentBowlers: [],
      partnership: '',
      lastWicket: '',
      recentBalls: '',
      toss: '',
      oversLeft: '',
      latestPerformance: [],
      commentary: [],
      ...overrides,
    },
    fetchedAt: new Date().toISOString(),
  });

  it('short-circuits upcoming matches without hitting providers', async () => {
    vi.spyOn(sportsService, 'getMatchDetails').mockResolvedValue(makeMatch({ state: 'upcoming' }) as any);

    const providers: HighlightsProviderSet = {
      bcci: { findHighlights: vi.fn() },
      ipl: { findHighlights: vi.fn() },
      youtube: { findHighlights: vi.fn() },
    };

    const res = await getMatchHighlights('101', providers);
    expect(res?.highlights).toEqual([]);
    expect(providers.bcci.findHighlights).not.toHaveBeenCalled();
    expect(providers.ipl.findHighlights).not.toHaveBeenCalled();
    expect(providers.youtube.findHighlights).not.toHaveBeenCalled();
  });

  it('falls back to YouTube when BCCI returns empty (e.g., India away tour)', async () => {
    vi.spyOn(sportsService, 'getMatchDetails').mockResolvedValue(makeMatch() as any);

    const ytHighlight = {
      id: 'youtube:abc123',
      title: 'Australia vs India 1st T20I Highlights',
      thumbnailUrl: null,
      duration: '8:00',
      durationSeconds: 480,
      publishedAt: '1d ago',
      source: 'youtube' as const,
      playbackType: 'youtube' as const,
      streamTarget: 'abc123',
      externalUrl: 'https://www.youtube.com/watch?v=abc123',
      publisher: 'cricket.com.au',
    };

    const providers: HighlightsProviderSet = {
      bcci: { findHighlights: vi.fn().mockResolvedValue([]) },
      ipl: { findHighlights: vi.fn() },
      youtube: { findHighlights: vi.fn().mockResolvedValue([ytHighlight]) },
    };

    const res = await getMatchHighlights('101', providers);
    expect(providers.bcci.findHighlights).toHaveBeenCalledOnce();
    expect(providers.youtube.findHighlights).toHaveBeenCalledOnce();
    expect(res?.highlights).toEqual([ytHighlight]);
  });

  it('routes IPL matches to iplt20 provider first', async () => {
    vi.spyOn(sportsService, 'getMatchDetails').mockResolvedValue(
      makeMatch({
        series: 'Indian Premier League 2026',
        team1: { id: '1', name: 'Royal Challengers Bengaluru', shortName: 'RCB', flagUrl: null },
        team2: { id: '2', name: 'Gujarat Titans', shortName: 'GT', flagUrl: null },
      }) as any,
    );

    const iplHighlight = {
      id: 'iplt20:1',
      title: 'IPL 2026 Final: RCB vs GT - Match Highlights',
      thumbnailUrl: null,
      duration: '14:05',
      durationSeconds: 845,
      publishedAt: '2026-06-01T18:00:00.000Z',
      source: 'iplt20' as const,
      playbackType: 'hls' as const,
      streamTarget: 'https://stream.mux.com/test.m3u8?token=abc',
      externalUrl: 'https://www.iplt20.com/videos/test',
      publisher: 'IPL',
      playbackHeaders: { Referer: 'https://www.iplt20.com/', Origin: 'https://www.iplt20.com' },
    };

    const providers: HighlightsProviderSet = {
      bcci: { findHighlights: vi.fn() },
      ipl: { findHighlights: vi.fn().mockResolvedValue([iplHighlight]) },
      youtube: { findHighlights: vi.fn() },
    };

    const res = await getMatchHighlights('101', providers);
    expect(providers.ipl.findHighlights).toHaveBeenCalledOnce();
    expect(providers.bcci.findHighlights).not.toHaveBeenCalled();
    expect(providers.youtube.findHighlights).not.toHaveBeenCalled();
    expect(res?.highlights).toEqual([iplHighlight]);
  });
});
