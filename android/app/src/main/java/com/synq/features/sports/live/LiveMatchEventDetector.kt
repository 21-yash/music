package com.synq.features.sports.live

import com.synq.features.sports.domain.model.BatsmanLive
import com.synq.features.sports.domain.model.LiveScore
import com.synq.features.sports.domain.model.MatchDetails
import com.synq.features.sports.domain.model.MatchSummary
import com.synq.features.sports.domain.model.Scorecard
import kotlin.math.round

enum class LiveEventType {
    FOUR,
    SIX,
    WICKET,
    NO_BALL,
    FIFTY,
    CENTURY
}

data class RecentBallItem(
    val id: Long,
    val label: String,
    val isNew: Boolean = false
)

data class LiveMatchEvent(
    val id: Long,
    val type: LiveEventType,
    val matchCardType: LiveEventType? = null,
    val batterEvents: Map<String, LiveEventType> = emptyMap(),
    val bowlerEvents: Map<String, LiveEventType> = emptyMap(),
    val battingTeamShort: String? = null,
    val transientDismissedBatter: BatsmanLive? = null,
    val replacedBatterName: String? = null,
    val isWicketSuperseded: Boolean = false
) {
    val highlightedBatterNames: Set<String> get() = batterEvents.keys
    val highlightedBowlerNames: Set<String> get() = bowlerEvents.keys

    fun hasPersistentState(): Boolean =
        batterEvents.values.any {
            it == LiveEventType.FIFTY ||
                it == LiveEventType.CENTURY ||
                (it == LiveEventType.WICKET && !isWicketSuperseded)
        }

    fun retainedPersistentOnly(): LiveMatchEvent? {
        val persistentBatters = batterEvents.filterValues {
            it == LiveEventType.FIFTY ||
                it == LiveEventType.CENTURY ||
                (it == LiveEventType.WICKET && !isWicketSuperseded)
        }
        if (persistentBatters.isEmpty()) return null
        val retainedType = persistentBatters.values.first()
        return copy(
            type = retainedType,
            matchCardType = null,
            batterEvents = persistentBatters,
            bowlerEvents = emptyMap()
        )
    }
}

data class ParsedTeamScore(
    val isYetToBat: Boolean,
    val staticPrefix: String,
    val currentScore: String,
    val oversSuffix: String
)

private data class ActiveInningsProgress(
    val teamSlot: Int, // 1 or 2
    val totalInnings: Int,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    val oversText: String
)

/**
 * Pure snapshot diff engine comparing consecutive live [MatchDetails] payloads
 * (typically emitted every ~15s over Socket.IO) to resolve independent per-card
 * animations for Match Card (`4`/`6`/`W`/`NB`), Batsman Rows (`50`/`100`/`W`/`4`/`6`),
 * and Bowler Rows (`W`).
 */
object LiveMatchEventDetector {

    private val NO_BALL_REGEX = Regex("""^(?:\d*n(?:b)?|n(?:b)?\d*|noball)$""", RegexOption.IGNORE_CASE)

    fun detect(
        previous: MatchDetails?,
        next: MatchDetails,
        eventId: Long
    ): LiveMatchEvent? {
        if (previous == null || !next.summary.isLive) return null
        if (previous.summary.id != next.summary.id) return null

        val newBalls = extractNewRecentBalls(previous.recentBalls, next.recentBalls)
        val isMultiBallBatch = newBalls.size > 1
        val latestBall = newBalls.lastOrNull()

        // Bowler wicket delta
        val prevBowlersByName = previous.currentBowlers.associateBy { normalizeName(it.name) }
        val wicketBowlersDelta = next.currentBowlers
            .filter { nextBowl ->
                val prevBowl = prevBowlersByName[normalizeName(nextBowl.name)]
                prevBowl != null && nextBowl.wickets > prevBowl.wickets
            }
            .associate { normalizeName(it.name) to LiveEventType.WICKET }

        val prevBattersByName = previous.currentBatsmen.associateBy { normalizeName(it.name) }
        val batterEvents = mutableMapOf<String, LiveEventType>()

        val isWicket: Boolean
        val isNoBall: Boolean
        var anySix: Boolean
        var anyFour: Boolean
        val bowlerEvents: Map<String, LiveEventType>

        if (isMultiBallBatch && latestBall != null) {
            // Multiple deliveries arrived at once: animate ONLY the most recent ball and nothing else.
            isWicket = isWicketToken(latestBall)
            isNoBall = isNoBallToken(latestBall)
            anySix = isSixToken(latestBall)
            anyFour = isFourToken(latestBall)
            bowlerEvents = if (isWicket) wicketBowlersDelta else emptyMap()

            if (!isWicket) {
                val latestRuns = parseBallRuns(latestBall)
                val scorer = resolveLatestBallBatter(
                    previous = previous,
                    next = next,
                    prevBattersByName = prevBattersByName,
                    isSix = anySix,
                    isFour = anyFour,
                    latestRuns = latestRuns
                )
                if (scorer != null) {
                    val key = normalizeName(scorer.name)
                    val prevRuns = prevBattersByName[key]?.runs ?: 0
                    val crossedCentury = latestRuns > 0 && prevRuns < 100 && scorer.runs >= 100 && (scorer.runs - latestRuns) < 100
                    val crossedFifty = latestRuns > 0 && prevRuns < 50 && scorer.runs >= 50 && (scorer.runs - latestRuns) < 50
                    val event = when {
                        crossedCentury -> LiveEventType.CENTURY
                        crossedFifty -> LiveEventType.FIFTY
                        anySix -> LiveEventType.SIX
                        anyFour -> LiveEventType.FOUR
                        else -> null
                    }
                    if (event != null) {
                        batterEvents[key] = event
                    }
                }
            }
        } else {
            val wicketInBalls = latestBall != null && isWicketToken(latestBall)
            val lastWicketChanged = next.lastWicket.isNotBlank() && next.lastWicket != previous.lastWicket
            isWicket = wicketBowlersDelta.isNotEmpty() || wicketInBalls || lastWicketChanged
            isNoBall = latestBall != null && isNoBallToken(latestBall)
            anySix = latestBall != null && isSixToken(latestBall)
            anyFour = latestBall != null && isFourToken(latestBall)
            bowlerEvents = if (isWicket) wicketBowlersDelta else emptyMap()

            for (nextBat in next.currentBatsmen) {
                val key = normalizeName(nextBat.name)
                val prevBat = prevBattersByName[key] ?: continue

                val batSix = nextBat.sixes > prevBat.sixes
                val batFour = nextBat.fours > prevBat.fours
                if (batSix) anySix = true
                if (batFour) anyFour = true

                val batEvent = when {
                    prevBat.runs < 100 && nextBat.runs >= 100 -> LiveEventType.CENTURY
                    prevBat.runs < 50 && nextBat.runs >= 50 -> LiveEventType.FIFTY
                    batSix -> LiveEventType.SIX
                    batFour -> LiveEventType.FOUR
                    nextBat.runs > prevBat.runs && anySix && !anyFour -> LiveEventType.SIX
                    nextBat.runs > prevBat.runs && anyFour && !anySix -> LiveEventType.FOUR
                    else -> null
                }
                if (batEvent != null) {
                    batterEvents[key] = batEvent
                }
            }
        }

        // Apply WICKET onto the out batsman (higher priority than boundary for the dismissed batter)
        var transientDismissed: BatsmanLive? = null
        var replacedBatterName: String? = null
        if (isWicket) {
            val (outNames, dismissed) = findOutBatterResult(previous, next)
            transientDismissed = dismissed
            if (dismissed != null) {
                val prevNames = previous.currentBatsmen.mapTo(mutableSetOf()) { normalizeName(it.name) }
                replacedBatterName = next.currentBatsmen
                    .firstOrNull { normalizeName(it.name) !in prevNames }
                    ?.let { normalizeName(it.name) }
            }
            for (outKey in outNames) {
                batterEvents[outKey] = LiveEventType.WICKET
            }
        }

        // Match Summary Card: shows team-level WICKET, NO_BALL, SIX, FOUR (milestones stay on Batter Row)
        val matchCardType = when {
            isWicket -> LiveEventType.WICKET
            isNoBall -> LiveEventType.NO_BALL
            anySix -> LiveEventType.SIX
            anyFour -> LiveEventType.FOUR
            else -> null
        }

        val overallType = when {
            isWicket -> LiveEventType.WICKET
            isNoBall -> LiveEventType.NO_BALL
            batterEvents.containsValue(LiveEventType.CENTURY) -> LiveEventType.CENTURY
            batterEvents.containsValue(LiveEventType.FIFTY) -> LiveEventType.FIFTY
            anySix -> LiveEventType.SIX
            anyFour -> LiveEventType.FOUR
            else -> return null
        }

        val activeLiveScore = next.summary.liveScore?.score?.trim().orEmpty()
        val battingTeamShort = when {
            next.summary.team2Score != previous.summary.team2Score -> next.summary.team2.shortName
            next.summary.team1Score != previous.summary.team1Score -> next.summary.team1.shortName
            activeLiveScore.isNotBlank() && next.summary.team2Score?.contains(activeLiveScore) == true ->
                next.summary.team2.shortName
            activeLiveScore.isNotBlank() && next.summary.team1Score?.contains(activeLiveScore) == true ->
                next.summary.team1.shortName
            !next.summary.team2Score.isNullOrBlank() &&
                !next.summary.team2Score.equals("Yet to bat", ignoreCase = true) ->
                next.summary.team2.shortName
            !next.summary.team1Score.isNullOrBlank() &&
                !next.summary.team1Score.equals("Yet to bat", ignoreCase = true) ->
                next.summary.team1.shortName
            else -> null
        }

        return LiveMatchEvent(
            id = eventId,
            type = overallType,
            matchCardType = matchCardType,
            batterEvents = batterEvents,
            bowlerEvents = bowlerEvents,
            battingTeamShort = battingTeamShort,
            transientDismissedBatter = transientDismissed,
            replacedBatterName = replacedBatterName,
            isWicketSuperseded = false
        )
    }

    /**
     * Returns true when [next] represents a stale CDN rollback relative to [previous]
     * in the same active live innings (e.g. score or overs went strictly backwards).
     */
    fun isStaleRollback(previous: MatchDetails, next: MatchDetails): Boolean {
        if (previous.summary.id != next.summary.id) return false
        if (!previous.summary.isLive || !next.summary.isLive) return false

        val prevProg = resolveActiveInningsProgress(previous.summary) ?: return false
        val nextProg = resolveActiveInningsProgress(next.summary) ?: return false

        // Different innings or team began batting -> never treat as rollback
        if (prevProg.teamSlot != nextProg.teamSlot || prevProg.totalInnings != nextProg.totalInnings) {
            return false
        }

        if (nextProg.legalBalls < prevProg.legalBalls) return true
        if (nextProg.wickets < prevProg.wickets) return true
        if (nextProg.runs < prevProg.runs && nextProg.legalBalls <= prevProg.legalBalls) return true

        val prevBattersByName = previous.currentBatsmen.associateBy { normalizeName(it.name) }
        for (nextBat in next.currentBatsmen) {
            val prevBat = prevBattersByName[normalizeName(nextBat.name)] ?: continue
            if (nextBat.balls < prevBat.balls) return true
            if (nextBat.runs < prevBat.runs && nextBat.balls <= prevBat.balls) return true
        }

        if (next.recentBalls != previous.recentBalls &&
            nextProg.legalBalls <= prevProg.legalBalls &&
            extractNewRecentBalls(next.recentBalls, previous.recentBalls).isNotEmpty()
        ) {
            return true
        }

        return false
    }

    /**
     * Rejects stale HTTP [incoming] scorecards whose active innings lags behind [current].
     */
    fun isScorecardStale(current: Scorecard?, incoming: Scorecard): Boolean {
        if (current == null || current.matchId != incoming.matchId) return false
        if (incoming.innings.size < current.innings.size) return true
        if (incoming.innings.size > current.innings.size || current.innings.isEmpty()) return false

        val curLast = current.innings.last()
        val incLast = incoming.innings.last()
        val curBalls = parseOversToBalls(curLast.overs) ?: return false
        val incBalls = parseOversToBalls(incLast.overs) ?: return false
        if (incBalls < curBalls) return true

        val curRuns = parseScoreRunsWkts(curLast.score)?.first ?: return false
        val incRuns = parseScoreRunsWkts(incLast.score)?.first ?: return false
        return incBalls == curBalls && incRuns < curRuns
    }

    /**
     * Synchronizes [scorecard]'s active innings header and not-out batters/bowlers with fresher
     * live [details] whenever the backend's scorecard cache lags behind real-time Socket updates.
     */
    fun reconcileScorecardWithLiveDetails(
        scorecard: Scorecard,
        details: MatchDetails
    ): Scorecard {
        if (!details.summary.isLive || scorecard.innings.isEmpty()) return scorecard
        val activeProg = resolveActiveInningsProgress(
            summary = details.summary,
            preferredTeamName = scorecard.innings.last().battingTeam
        ) ?: return scorecard
        val activeTeam = if (activeProg.teamSlot == 1) details.summary.team1 else details.summary.team2

        var targetIdx = scorecard.innings.indexOfLast { inn ->
            teamMatches(inn.battingTeam, activeTeam.name, activeTeam.shortName)
        }
        if (targetIdx < 0) targetIdx = scorecard.innings.lastIndex

        val targetInning = scorecard.innings[targetIdx]
        val scBalls = parseOversToBalls(targetInning.overs) ?: -1
        val scRuns = parseScoreRunsWkts(targetInning.score)?.first ?: -1

        if (activeProg.legalBalls < scBalls) return scorecard
        if (activeProg.legalBalls == scBalls && activeProg.runs <= scRuns) return scorecard

        val separator = if (targetInning.score.contains('/')) '/' else '-'
        val updatedScore = "${activeProg.runs}$separator${activeProg.wickets}"
        val updatedOvers = activeProg.oversText.ifBlank { targetInning.overs }

        val liveBatByName = details.currentBatsmen.associateBy { normalizeName(it.name) }
        val updatedBatting = targetInning.batting.map { scBat ->
            val liveBat = liveBatByName[normalizeName(scBat.name)]
            if (liveBat != null && (liveBat.balls > scBat.balls || liveBat.runs > scBat.runs)) {
                scBat.copy(
                    runs = maxOf(scBat.runs, liveBat.runs),
                    balls = maxOf(scBat.balls, liveBat.balls),
                    fours = maxOf(scBat.fours, liveBat.fours),
                    sixes = maxOf(scBat.sixes, liveBat.sixes),
                    strikeRate = liveBat.strikeRate
                )
            } else {
                scBat
            }
        }

        val liveBowlByName = details.currentBowlers.associateBy { normalizeName(it.name) }
        val updatedBowling = targetInning.bowling.map { scBowl ->
            val liveBowl = liveBowlByName[normalizeName(scBowl.name)]
            val scBowlBalls = parseOversToBalls(scBowl.overs) ?: -1
            val liveBowlBalls = liveBowl?.let { parseOversToBalls(it.overs) } ?: -1
            if (liveBowl != null && (liveBowlBalls > scBowlBalls || liveBowl.runs > scBowl.runs || liveBowl.wickets > scBowl.wickets)) {
                scBowl.copy(
                    overs = if (liveBowlBalls >= scBowlBalls) liveBowl.overs else scBowl.overs,
                    maidens = maxOf(scBowl.maidens, liveBowl.maidens),
                    runs = maxOf(scBowl.runs, liveBowl.runs),
                    wickets = maxOf(scBowl.wickets, liveBowl.wickets),
                    economy = liveBowl.economy
                )
            } else {
                scBowl
            }
        }

        val updatedInning = targetInning.copy(
            score = updatedScore,
            overs = updatedOvers,
            batting = updatedBatting,
            bowling = updatedBowling
        )
        return scorecard.copy(
            innings = scorecard.innings.toMutableList().apply { set(targetIdx, updatedInning) }
        )
    }

    private fun resolveLatestBallBatter(
        previous: MatchDetails,
        next: MatchDetails,
        prevBattersByName: Map<String, BatsmanLive>,
        isSix: Boolean,
        isFour: Boolean,
        latestRuns: Int
    ): BatsmanLive? {
        if (isSix) {
            return next.currentBatsmen.firstOrNull { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)] ?: return@firstOrNull false
                bat.sixes > prev.sixes
            } ?: next.currentBatsmen.filter { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)] ?: return@filter false
                bat.runs - prev.runs >= 6
            }.maxByOrNull { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)]
                bat.runs - (prev?.runs ?: 0)
            }
        }
        if (isFour) {
            return next.currentBatsmen.firstOrNull { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)] ?: return@firstOrNull false
                bat.fours > prev.fours
            } ?: next.currentBatsmen.filter { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)] ?: return@filter false
                bat.runs - prev.runs >= 4
            }.maxByOrNull { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)]
                bat.runs - (prev?.runs ?: 0)
            }
        }
        if (latestRuns > 0) {
            val scorers = next.currentBatsmen.filter { bat ->
                val prev = prevBattersByName[normalizeName(bat.name)] ?: return@filter false
                bat.runs - prev.runs > 0
            }
            return scorers.firstOrNull { it.onStrike } ?: scorers.firstOrNull()
        }
        return null
    }

    private fun findOutBatterResult(
        previous: MatchDetails,
        next: MatchDetails
    ): Pair<Set<String>, BatsmanLive?> {
        val normalizedLastWicket = normalizeName(next.lastWicket)
        if (normalizedLastWicket.isNotBlank()) {
            val matchedInNext = matchBatterByText(next.currentBatsmen, normalizedLastWicket)
            if (matchedInNext != null) {
                return setOf(normalizeName(matchedInNext.name)) to null
            }
            val matchedInPrev = matchBatterByText(previous.currentBatsmen, normalizedLastWicket)
            if (matchedInPrev != null) {
                val norm = normalizeName(matchedInPrev.name)
                return setOf(norm) to enrichDismissedStats(matchedInPrev, next.lastWicket)
            }
        }

        val nextNames = next.currentBatsmen.mapTo(mutableSetOf()) { normalizeName(it.name) }
        val disappeared = previous.currentBatsmen.filter { normalizeName(it.name) !in nextNames }
        if (disappeared.size == 1) {
            val out = disappeared.first()
            return setOf(normalizeName(out.name)) to enrichDismissedStats(out, next.lastWicket)
        }

        val prevStriker = previous.currentBatsmen.firstOrNull { it.onStrike }?.let { normalizeName(it.name) }
        return if (prevStriker != null && prevStriker in nextNames) {
            setOf(prevStriker) to null
        } else {
            emptySet<String>() to null
        }
    }

    private fun enrichDismissedStats(bat: BatsmanLive, lastWicketText: String): BatsmanLive {
        val m = Regex("""(\d+)\((\d+)\)""").find(lastWicketText)
        val finalRuns = m?.groupValues?.getOrNull(1)?.toIntOrNull()
        val finalBalls = m?.groupValues?.getOrNull(2)?.toIntOrNull()
        return if (finalRuns != null && finalBalls != null && finalBalls > 0) {
            val sr = ((finalRuns * 1000.0) / finalBalls).let { round(it) / 10.0 }
            bat.copy(runs = finalRuns, balls = finalBalls, strikeRate = sr, onStrike = false)
        } else {
            bat.copy(onStrike = false)
        }
    }

    private fun matchBatterByText(batters: List<BatsmanLive>, normalizedText: String): BatsmanLive? {
        val exact = batters.firstOrNull { normalizedText.contains(normalizeName(it.name)) }
        if (exact != null) return exact
        val byLastToken = batters.filter { bat ->
            val lastToken = normalizeName(bat.name).substringAfterLast(' ')
            lastToken.length >= 3 && normalizedText.contains(lastToken)
        }
        return if (byLastToken.size == 1) byLastToken.first() else null
    }

    fun isWicketToken(token: String): Boolean {
        val t = token.trim()
        return t.equals("W", ignoreCase = true) || t.equals("Wicket", ignoreCase = true)
    }

    fun isFourToken(token: String): Boolean {
        val t = token.trim()
        return t == "4" || t.equals("Four", ignoreCase = true) ||
            (isNoBallToken(t) && t.contains('4'))
    }

    fun isSixToken(token: String): Boolean {
        val t = token.trim()
        return t == "6" || t.equals("Six", ignoreCase = true) ||
            (isNoBallToken(t) && t.contains('6'))
    }

    fun isNoBallToken(token: String): Boolean {
        val cleaned = token.trim().replace("+", "")
        return NO_BALL_REGEX.matches(cleaned)
    }

    private fun parseBallRuns(token: String): Int {
        val t = token.trim()
        if (isWicketToken(t)) return 0
        if (t.equals("Wd", ignoreCase = true) || t.startsWith("Wd", ignoreCase = true)) return 0
        if (t.equals("Four", ignoreCase = true)) return 4
        if (t.equals("Six", ignoreCase = true)) return 6
        val direct = t.toIntOrNull()
        if (direct != null) return direct
        if (isNoBallToken(t)) {
            val digit = Regex("""(\d+)""").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            return digit
        }
        return Regex("""(\d+)""").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    /**
     * Splits a single- or multi-innings score string (e.g. `"146-2 (16.3)"` or
     * `"384-10 (112.4) & 146-4 (68.3)"`) so only the active bold current-innings score animates.
     */
    fun parseTeamScore(raw: String): ParsedTeamScore {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("Yet to bat", ignoreCase = true)) {
            return ParsedTeamScore(isYetToBat = true, staticPrefix = "", currentScore = "", oversSuffix = "")
        }

        val ampIdx = trimmed.lastIndexOf(" & ")
        val rawPrefix = if (ampIdx >= 0) trimmed.substring(0, ampIdx + 3) else ""
        val staticPrefix = if (rawPrefix.isNotEmpty()) {
            rawPrefix.replace(Regex("""\s*\([^)]*\)"""), "")
        } else {
            ""
        }
        val activePart = if (ampIdx >= 0) trimmed.substring(ampIdx + 3).trim() else trimmed

        val parenIdx = activePart.indexOf('(')
        val currentScore = if (parenIdx >= 0) activePart.substring(0, parenIdx).trim() else activePart
        val oversSuffix = if (parenIdx >= 0) " ${activePart.substring(parenIdx).trim()}" else ""

        return ParsedTeamScore(
            isYetToBat = false,
            staticPrefix = staticPrefix,
            currentScore = currentScore,
            oversSuffix = oversSuffix
        )
    }

    fun hasMultipleInnings(summary: MatchSummary): Boolean =
        summary.team1Score.orEmpty().contains(" & ") ||
            summary.team2Score.orEmpty().contains(" & ")

    /**
     * Splits a single- or multi-innings score string into individual innings parts with full overs
     * preserved (e.g. `"384-10 (112.4)"`, `"212-10 (61.2)"`) for the multi-innings breakdown popup.
     */
    fun splitInnings(raw: String): List<ParsedTeamScore> {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("Yet to bat", ignoreCase = true)) return emptyList()
        return trimmed.split(" & ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { part -> parseTeamScore(part) }
            .filter { !it.isYetToBat }
    }

    /**
     * Extract only the newly appended deliveries between [previousRaw] and [nextRaw]
     * using longest suffix-prefix overlap (handles sliding-window recent ball lists).
     */
    fun extractNewRecentBalls(previousRaw: String, nextRaw: String): List<String> {
        val prev = parseBalls(previousRaw)
        val next = parseBalls(nextRaw)
        if (next.isEmpty()) return emptyList()
        if (prev.isEmpty()) return next.takeLast(1)
        if (prev == next) return emptyList()

        val maxOverlap = minOf(prev.size, next.size)
        for (overlap in maxOverlap downTo 1) {
            val prevSuffix = prev.subList(prev.size - overlap, prev.size)
            val nextPrefix = next.subList(0, overlap)
            if (prevSuffix == nextPrefix) {
                return next.subList(overlap, next.size)
            }
        }

        // If recent balls reset cleanly (e.g. new innings) with just a couple of deliveries
        return if (next.size <= 3) next else emptyList()
    }

    fun parseBalls(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(" ", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Ensures first-batting team is shown on top and chasing / yet-to-bat team is shown at bottom.
     */
    fun isTeam1BattingFirst(summary: MatchSummary): Boolean {
        val s1 = summary.team1Score.orEmpty().trim()
        val s2 = summary.team2Score.orEmpty().trim()
        val t1Yet = s1.isBlank() || s1.equals("Yet to bat", ignoreCase = true)
        val t2Yet = s2.isBlank() || s2.equals("Yet to bat", ignoreCase = true)

        if (t1Yet && !t2Yet) return false
        if (!t1Yet && t2Yet) return true
        if (t1Yet && t2Yet) return true

        val activeScore = summary.liveScore?.score?.trim().orEmpty()
        if (activeScore.isNotBlank()) {
            val t1Matches = s1.contains(activeScore)
            val t2Matches = s2.contains(activeScore)
            if (t1Matches && !t2Matches) return false
            if (t2Matches && !t1Matches) return true
        }

        if (summary.isLive) {
            val statusLower = summary.status.lowercase()
            if (statusLower.contains("need") || statusLower.contains("require")) {
                val t1Matched = statusMatchesTeam(statusLower, summary.team1.name, summary.team1.shortName)
                val t2Matched = statusMatchesTeam(statusLower, summary.team2.name, summary.team2.shortName)
                if (t1Matched != t2Matched) {
                    return !t1Matched
                }
            }
        }

        if (!summary.isLive && summary.status.contains("won by", ignoreCase = true)) {
            val statusLower = summary.status.lowercase()
            val wonByWickets = statusLower.contains("wkt") || statusLower.contains("wicket")
            val wonByRuns = statusLower.contains("run")
            val t1Matched = statusMatchesTeam(statusLower, summary.team1.name, summary.team1.shortName)
            val t2Matched = statusMatchesTeam(statusLower, summary.team2.name, summary.team2.shortName)
            if (t1Matched != t2Matched) {
                if (wonByWickets) return !t1Matched
                if (wonByRuns) return t1Matched
            }
        }

        return true
    }

    private fun resolveActiveInningsProgress(
        summary: MatchSummary,
        preferredTeamName: String? = null
    ): ActiveInningsProgress? {
        val s1 = summary.team1Score.orEmpty().trim()
        val s2 = summary.team2Score.orEmpty().trim()
        val c1 = countInnings(s1)
        val c2 = countInnings(s2)
        val totalInnings = c1 + c2
        if (totalInnings == 0) return null

        val activeRaw = summary.liveScore?.score?.trim().orEmpty()
        val slot = when {
            activeRaw.isNotBlank() && s1.contains(activeRaw) && !s2.contains(activeRaw) -> 1
            activeRaw.isNotBlank() && s2.contains(activeRaw) && !s1.contains(activeRaw) -> 2
            !preferredTeamName.isNullOrBlank() &&
                teamMatches(preferredTeamName, summary.team1.name, summary.team1.shortName) &&
                !teamMatches(preferredTeamName, summary.team2.name, summary.team2.shortName) -> 1
            !preferredTeamName.isNullOrBlank() &&
                teamMatches(preferredTeamName, summary.team2.name, summary.team2.shortName) &&
                !teamMatches(preferredTeamName, summary.team1.name, summary.team1.shortName) -> 2
            c2 > 0 && c1 <= c2 && !s2.equals("Yet to bat", ignoreCase = true) -> {
                // In limited overs chase, if both have batted once, the chasing side is team1 or team2 depending on order
                if (isTeam1BattingFirst(summary)) 2 else 1
            }
            else -> 1
        }
        val targetScoreStr = if (slot == 1) s1 else s2
        val parsed = parseTeamScore(targetScoreStr)
        if (parsed.isYetToBat) return null
        val (runs, wkts) = parseScoreRunsWkts(parsed.currentScore) ?: return null
        val oversText = parsed.oversSuffix.trim().removePrefix("(").removeSuffix(")").trim()
        val legalBalls = parseOversToBalls(oversText) ?: 0
        return ActiveInningsProgress(
            teamSlot = slot,
            totalInnings = totalInnings,
            runs = runs,
            wickets = wkts,
            legalBalls = legalBalls,
            oversText = oversText
        )
    }

    private fun countInnings(raw: String): Int {
        val t = raw.trim()
        if (t.isBlank() || t.equals("Yet to bat", ignoreCase = true)) return 0
        return t.split(" & ").count { it.isNotBlank() }
    }

    private fun parseScoreRunsWkts(raw: String): Pair<Int, Int>? {
        val m = Regex("""^(\d+)(?:[/-](\d+))?""").find(raw.trim()) ?: return null
        val runs = m.groupValues[1].toIntOrNull() ?: return null
        val wkts = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return runs to wkts
    }

    private fun parseOversToBalls(overs: String): Int? {
        val clean = overs.trim().removeSuffix("ov").removeSuffix("ov.").trim()
        if (clean.isBlank()) return null
        val parts = clean.split(".")
        val ov = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val b = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return ov * 6 + b
    }

    private fun teamMatches(candidate: String, fullName: String, shortName: String): Boolean {
        val c = candidate.trim().lowercase()
        val f = fullName.trim().lowercase()
        val s = shortName.trim().lowercase()
        return (f.isNotBlank() && (c == f || c.contains(f) || f.contains(c))) ||
            (s.isNotBlank() && (c == s || Regex("""\b${Regex.escape(s)}\b""").containsMatchIn(c)))
    }

    private fun statusMatchesTeam(statusLower: String, fullName: String, shortName: String): Boolean {
        val full = fullName.trim().lowercase()
        val short = shortName.trim().lowercase()
        return (full.isNotBlank() && statusLower.contains(full)) ||
            (short.isNotBlank() && Regex("""\b${Regex.escape(short)}\b""").containsMatchIn(statusLower))
    }

    fun normalizeName(name: String): String = name.trim().lowercase()
}
