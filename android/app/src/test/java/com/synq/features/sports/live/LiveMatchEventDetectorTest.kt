package com.synq.features.sports.live

import com.synq.features.sports.domain.model.BatsmanInnings
import com.synq.features.sports.domain.model.BatsmanLive
import com.synq.features.sports.domain.model.BowlerInnings
import com.synq.features.sports.domain.model.BowlerLive
import com.synq.features.sports.domain.model.InningsScorecard
import com.synq.features.sports.domain.model.MatchDetails
import com.synq.features.sports.domain.model.MatchFormat
import com.synq.features.sports.domain.model.MatchState
import com.synq.features.sports.domain.model.MatchSummary
import com.synq.features.sports.domain.model.Scorecard
import com.synq.features.sports.domain.model.ScorecardMatchInfo
import com.synq.features.sports.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatchEventDetectorTest {

    private fun makeMatch(
        isLive: Boolean = true,
        team1Score: String = "172/3 (17.2)",
        recentBalls: String = "1 4 0 6 1 2",
        lastWicket: String = "V. Kohli 52(34)",
        batsmen: List<BatsmanLive> = listOf(
            BatsmanLive("S. Yadav", runs = 49, balls = 24, fours = 5, sixes = 2, strikeRate = 204.1, onStrike = true),
            BatsmanLive("H. Pandya", runs = 99, balls = 46, fours = 8, sixes = 6, strikeRate = 215.2, onStrike = false)
        ),
        bowlers: List<BowlerLive> = listOf(
            BowlerLive("M. Starc", overs = "3.2", maidens = 0, runs = 34, wickets = 1, economy = 10.2)
        )
    ) = MatchDetails(
        summary = MatchSummary(
            id = "999",
            title = "IND vs AUS",
            matchDesc = "1st T20I",
            series = "AUS tour of IND",
            venue = "Mumbai",
            state = if (isLive) MatchState.LIVE else MatchState.COMPLETED,
            format = MatchFormat.T20,
            team1 = Team("1", "India", "IND"),
            team2 = Team("2", "Australia", "AUS"),
            team1Score = team1Score,
            team2Score = "188/6 (20.0)",
            status = "IND need 17 runs",
            startTime = 0L,
            seriesName = "AUS tour of IND",
            isLive = isLive
        ),
        playerOfMatch = null,
        currentBatsmen = batsmen,
        currentBowlers = bowlers,
        partnership = "40(20)",
        lastWicket = lastWicket,
        recentBalls = recentBalls,
        toss = "",
        oversLeft = "",
        latestPerformance = emptyList(),
        commentary = emptyList()
    )

    @Test
    fun `returns null on initial load and non-live matches`() {
        val current = makeMatch()
        assertNull(LiveMatchEventDetector.detect(null, current, 1L))

        val completedPrev = makeMatch(isLive = false)
        val completedNext = makeMatch(isLive = false, recentBalls = "1 4 0 6 1 2 6")
        assertNull(LiveMatchEventDetector.detect(completedPrev, completedNext, 2L))
    }

    @Test
    fun `detects FOUR and SIX and highlights scoring batter`() {
        val prev = makeMatch(
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 20, balls = 14, fours = 2, sixes = 0, strikeRate = 142.8, onStrike = true)
            )
        )

        val fourNext = makeMatch(
            team1Score = "176/3 (17.3)",
            recentBalls = "4 0 6 1 2 4",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 24, balls = 15, fours = 3, sixes = 0, strikeRate = 160.0, onStrike = true)
            )
        )
        val fourEvent = LiveMatchEventDetector.detect(prev, fourNext, 10L)
        assertNotNull(fourEvent)
        assertEquals(LiveEventType.FOUR, fourEvent!!.type)
        assertEquals("IND", fourEvent.battingTeamShort)
        assertTrue("s. yadav" in fourEvent.highlightedBatterNames)

        val sixNext = makeMatch(
            team1Score = "178/3 (17.3)",
            recentBalls = "4 0 6 1 2 6",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 26, balls = 15, fours = 2, sixes = 1, strikeRate = 173.3, onStrike = true)
            )
        )
        val sixEvent = LiveMatchEventDetector.detect(prev, sixNext, 11L)
        assertEquals(LiveEventType.SIX, sixEvent!!.type)
    }

    @Test
    fun `detects FIFTY and CENTURY even on a single run`() {
        val prev = makeMatch()
        val fiftyNext = makeMatch(
            team1Score = "173/3 (17.3)",
            recentBalls = "4 0 6 1 2 1",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 50, balls = 25, fours = 5, sixes = 2, strikeRate = 200.0, onStrike = false),
                BatsmanLive("H. Pandya", runs = 99, balls = 46, fours = 8, sixes = 6, strikeRate = 215.2, onStrike = true)
            )
        )
        val fiftyEvent = LiveMatchEventDetector.detect(prev, fiftyNext, 12L)
        assertEquals(LiveEventType.FIFTY, fiftyEvent!!.type)
        assertEquals(setOf("s. yadav"), fiftyEvent.highlightedBatterNames)

        val centuryNext = makeMatch(
            team1Score = "173/3 (17.3)",
            recentBalls = "4 0 6 1 2 1",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 49, balls = 24, fours = 5, sixes = 2, strikeRate = 204.1, onStrike = true),
                BatsmanLive("H. Pandya", runs = 100, balls = 47, fours = 8, sixes = 6, strikeRate = 212.7, onStrike = false)
            )
        )
        val centuryEvent = LiveMatchEventDetector.detect(prev, centuryNext, 13L)
        assertEquals(LiveEventType.CENTURY, centuryEvent!!.type)
        assertEquals(setOf("h. pandya"), centuryEvent.highlightedBatterNames)
    }

    @Test
    fun `wicket takes priority over boundary in same snapshot`() {
        val prev = makeMatch()
        val next = makeMatch(
            team1Score = "176/4 (17.4)",
            recentBalls = "0 6 1 2 4 W",
            lastWicket = "S. Yadav 53(25)",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 53, balls = 25, fours = 6, sixes = 2, strikeRate = 212.0, onStrike = true)
            ),
            bowlers = listOf(
                BowlerLive("M. Starc", overs = "3.4", maidens = 0, runs = 38, wickets = 2, economy = 10.3)
            )
        )
        val event = LiveMatchEventDetector.detect(prev, next, 14L)
        assertEquals(LiveEventType.WICKET, event!!.type)
        assertEquals(setOf("m. starc"), event.highlightedBowlerNames)
        assertEquals(setOf("s. yadav"), event.highlightedBatterNames)
    }

    @Test
    fun `extractNewRecentBalls handles sliding window cleanly`() {
        val diff = LiveMatchEventDetector.extractNewRecentBalls("1 4 0 6 1 2", "0 6 1 2 4 W")
        assertEquals(listOf("4", "W"), diff)
    }

    @Test
    fun `captures dismissed batter removed from next currentBatsmen`() {
        val prev = makeMatch()
        val next = makeMatch(
            team1Score = "172-4 (17.3)",
            recentBalls = "4 0 6 1 2 W",
            lastWicket = "S. Yadav c Carey b Starc 49(24)",
            batsmen = listOf(
                BatsmanLive("H. Pandya", runs = 99, balls = 46, fours = 8, sixes = 6, strikeRate = 215.2, onStrike = true),
                BatsmanLive("R. Jadeja", runs = 0, balls = 0, fours = 0, sixes = 0, strikeRate = 0.0, onStrike = false)
            ),
            bowlers = listOf(
                BowlerLive("M. Starc", overs = "3.3", maidens = 0, runs = 34, wickets = 2, economy = 9.7)
            )
        )
        val event = LiveMatchEventDetector.detect(prev, next, 15L)
        assertNotNull(event)
        assertEquals(LiveEventType.WICKET, event!!.type)
        assertEquals(setOf("s. yadav"), event.highlightedBatterNames)
        assertEquals("S. Yadav", event.transientDismissedBatter?.name)
    }

    @Test
    fun `parseTeamScore isolates only current innings bold score`() {
        val single = LiveMatchEventDetector.parseTeamScore("146-2 (16.3)")
        assertEquals("", single.staticPrefix)
        assertEquals("146-2", single.currentScore)
        assertEquals(" (16.3)", single.oversSuffix)

        val testMatch = LiveMatchEventDetector.parseTeamScore("384-10 (112.4) & 146-4 (68.3)")
        assertEquals("384-10 & ", testMatch.staticPrefix)
        assertEquals("146-4", testMatch.currentScore)
        assertEquals(" (68.3)", testMatch.oversSuffix)

        val innings = LiveMatchEventDetector.splitInnings("384-10 (112.4) & 146-4 (68.3)")
        assertEquals(2, innings.size)
        assertEquals("384-10", innings[0].currentScore)
        assertEquals(" (112.4)", innings[0].oversSuffix)
        assertEquals("146-4", innings[1].currentScore)
        assertEquals(" (68.3)", innings[1].oversSuffix)
    }

    @Test
    fun `boundary milestone triggers boundary on Match Card and milestone on Batter Row`() {
        val prev = makeMatch(
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 46, balls = 24, fours = 5, sixes = 2, strikeRate = 191.6, onStrike = false),
                BatsmanLive("H. Pandya", runs = 96, balls = 45, fours = 8, sixes = 6, strikeRate = 213.3, onStrike = true)
            )
        )
        val centuryByFourNext = makeMatch(
            team1Score = "176-3 (17.3)",
            recentBalls = "4 0 6 1 2 4",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 46, balls = 24, fours = 5, sixes = 2, strikeRate = 191.6, onStrike = false),
                BatsmanLive("H. Pandya", runs = 100, balls = 46, fours = 9, sixes = 6, strikeRate = 217.4, onStrike = true)
            )
        )
        val event = LiveMatchEventDetector.detect(prev, centuryByFourNext, 20L)
        assertNotNull(event)
        assertEquals(LiveEventType.FOUR, event!!.matchCardType)
        assertEquals(LiveEventType.CENTURY, event.batterEvents["h. pandya"])
    }

    @Test
    fun `detects NO_BALL for siren animation independently of batter boundary`() {
        val prev = makeMatch()
        val nbNext = makeMatch(
            team1Score = "177-3 (17.2)",
            recentBalls = "4 0 6 1 2 4Nb",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 53, balls = 25, fours = 6, sixes = 2, strikeRate = 212.0, onStrike = true),
                BatsmanLive("H. Pandya", runs = 99, balls = 46, fours = 8, sixes = 6, strikeRate = 215.2, onStrike = false)
            )
        )
        val event = LiveMatchEventDetector.detect(prev, nbNext, 21L)
        assertNotNull(event)
        assertEquals(LiveEventType.NO_BALL, event!!.matchCardType)
        assertEquals(LiveEventType.FIFTY, event.batterEvents["s. yadav"])
    }

    @Test
    fun `detects replacedBatterName on single wicket and orders chasing team at bottom`() {
        val prev = makeMatch(
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 49, balls = 24, fours = 5, sixes = 2, strikeRate = 204.1, onStrike = false),
                BatsmanLive("H. Pandya", runs = 12, balls = 8, fours = 1, sixes = 0, strikeRate = 150.0, onStrike = true)
            )
        )
        val next = makeMatch(
            team1Score = "172-4 (17.3)",
            recentBalls = "4 0 6 1 2 W",
            lastWicket = "H. Pandya b Starc 12(9)",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 49, balls = 24, fours = 5, sixes = 2, strikeRate = 204.1, onStrike = false),
                BatsmanLive("R. Jadeja", runs = 0, balls = 0, fours = 0, sixes = 0, strikeRate = 0.0, onStrike = true)
            ),
            bowlers = listOf(
                BowlerLive("M. Starc", overs = "3.3", maidens = 0, runs = 34, wickets = 2, economy = 9.7)
            )
        )
        val event = LiveMatchEventDetector.detect(prev, next, 22L)
        assertNotNull(event)
        assertEquals(LiveEventType.WICKET, event?.matchCardType)
        assertEquals("r. jadeja", event?.replacedBatterName)
        assertEquals("H. Pandya", event?.transientDismissedBatter?.name)
        assertEquals(9, event?.transientDismissedBatter?.balls)

        val chaseSummary = prev.summary.copy(
            team1Score = "140/4 (15.0)",
            team2Score = "188/6 (20.0)",
            liveScore = com.synq.features.sports.domain.model.LiveScore(score = "140/4 (15.0)", runRate = "9.33")
        )
        assertEquals(false, LiveMatchEventDetector.isTeam1BattingFirst(chaseSummary))
    }

    @Test
    fun `multi-ball batch animates only most recent ball and nothing else`() {
        val prev = makeMatch(
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 45, balls = 24, fours = 5, sixes = 2, strikeRate = 187.5, onStrike = true),
                BatsmanLive("H. Pandya", runs = 12, balls = 8, fours = 1, sixes = 0, strikeRate = 150.0, onStrike = false)
            )
        )

        // ["4", "1"] -> latest ball is 1 -> no FOUR animation!
        val fourThenSingle = makeMatch(
            team1Score = "177-3 (17.4)",
            recentBalls = "0 6 1 2 4 1",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 50, balls = 26, fours = 6, sixes = 2, strikeRate = 192.3, onStrike = false),
                BatsmanLive("H. Pandya", runs = 12, balls = 8, fours = 1, sixes = 0, strikeRate = 150.0, onStrike = true)
            )
        )
        // ["4", "0"] -> latest ball is dot -> completely null (nothing animates)
        val fourThenDot = makeMatch(
            team1Score = "176-3 (17.4)",
            recentBalls = "0 6 1 2 4 0",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 49, balls = 26, fours = 6, sixes = 2, strikeRate = 188.5, onStrike = true),
                BatsmanLive("H. Pandya", runs = 12, balls = 8, fours = 1, sixes = 0, strikeRate = 150.0, onStrike = false)
            )
        )
        assertNull(LiveMatchEventDetector.detect(prev, fourThenDot, 30L))
        // In fourThenSingle, Yadav crossed 50 on ball 1 ("4"), NOT latest ball ("1": 50-1=49 ->Wait: 45->49->50? Wait 45+4+1=50!)
        val eventSingle50 = LiveMatchEventDetector.detect(prev, fourThenSingle, 31L)
        assertNull(eventSingle50?.matchCardType)

        // ["W", "0"] -> latest ball is 0 -> no wicket animation, no 3rd row!
        val wicketThenDot = makeMatch(
            team1Score = "172-4 (17.4)",
            recentBalls = "0 6 1 2 W 0",
            lastWicket = "H. Pandya b Starc 12(9)",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 45, balls = 24, fours = 5, sixes = 2, strikeRate = 187.5, onStrike = false),
                BatsmanLive("R. Jadeja", runs = 0, balls = 1, fours = 0, sixes = 0, strikeRate = 0.0, onStrike = true)
            ),
            bowlers = listOf(
                BowlerLive("M. Starc", overs = "3.4", maidens = 0, runs = 34, wickets = 2, economy = 9.3)
            )
        )
        assertNull(LiveMatchEventDetector.detect(prev, wicketThenDot, 32L))

        // ["1", "6"] -> latest ball is 6 -> animates SIX cleanly!
        val singleThenSix = makeMatch(
            team1Score = "179-3 (17.4)",
            recentBalls = "0 6 1 2 1 6",
            batsmen = listOf(
                BatsmanLive("S. Yadav", runs = 46, balls = 25, fours = 5, sixes = 2, strikeRate = 184.0, onStrike = false),
                BatsmanLive("H. Pandya", runs = 18, balls = 9, fours = 1, sixes = 1, strikeRate = 200.0, onStrike = true)
            )
        )
        val sixEvent = LiveMatchEventDetector.detect(prev, singleThenSix, 33L)
        assertNotNull(sixEvent)
        assertEquals(LiveEventType.SIX, sixEvent!!.matchCardType)
        assertEquals(LiveEventType.SIX, sixEvent.batterEvents["h. pandya"])
    }

    @Test
    fun `rejects stale CDN rollback and reconciles lagged scorecard`() {
        val fresh101 = makeMatch(
            team1Score = "178-3 (17.3)",
            recentBalls = "1 4 0 6 1 6",
            batsmen = listOf(
                BatsmanLive("V. Kohli", runs = 101, balls = 52, fours = 8, sixes = 4, strikeRate = 194.2, onStrike = true),
                BatsmanLive("S. Yadav", runs = 31, balls = 18, fours = 3, sixes = 1, strikeRate = 172.2, onStrike = false)
            )
        )
        val stale95 = makeMatch(
            team1Score = "172-3 (17.2)",
            recentBalls = "1 4 0 6 1",
            batsmen = listOf(
                BatsmanLive("V. Kohli", runs = 95, balls = 51, fours = 8, sixes = 3, strikeRate = 186.3, onStrike = true),
                BatsmanLive("S. Yadav", runs = 31, balls = 18, fours = 3, sixes = 1, strikeRate = 172.2, onStrike = false)
            )
        )
        assertTrue(LiveMatchEventDetector.isStaleRollback(fresh101, stale95))
        assertFalse(LiveMatchEventDetector.isStaleRollback(stale95, fresh101))

        val laggedScorecard = Scorecard(
            matchId = "999",
            matchInfo = ScorecardMatchInfo("IND vs AUS", "Series", "Mumbai", "Live"),
            status = "IND need 11",
            innings = listOf(
                InningsScorecard(
                    inningsNumber = 1,
                    battingTeam = "Australia",
                    score = "188-6",
                    overs = "20",
                    batting = emptyList(),
                    bowling = emptyList(),
                    extras = "0",
                    total = "188",
                    didNotBat = emptyList(),
                    fallOfWickets = emptyList(),
                    powerplays = emptyList()
                ),
                InningsScorecard(
                    inningsNumber = 2,
                    battingTeam = "India",
                    score = "172-3",
                    overs = "17.2",
                    batting = listOf(
                        BatsmanInnings("V. Kohli", "batting", 95, 51, 8, 3, 186.3)
                    ),
                    bowling = listOf(
                        BowlerInnings("M. Starc", "3.2", 0, 28, 1, 0, 0, 8.4)
                    ),
                    extras = "0",
                    total = "172",
                    didNotBat = emptyList(),
                    fallOfWickets = emptyList(),
                    powerplays = emptyList()
                )
            )
        )
        val synced = LiveMatchEventDetector.reconcileScorecardWithLiveDetails(laggedScorecard, fresh101)
        val inn2 = synced.innings[1]
        assertEquals("178-3", inn2.score)
        assertEquals("17.3", inn2.overs)
        assertEquals(101, inn2.batting.first().runs)
        assertEquals(52, inn2.batting.first().balls)
        assertTrue(LiveMatchEventDetector.isScorecardStale(synced, laggedScorecard))
    }
}
