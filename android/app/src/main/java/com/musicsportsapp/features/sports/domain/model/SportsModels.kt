package com.musicsportsapp.features.sports.domain.model

enum class MatchState {
    LIVE, UPCOMING, COMPLETED, ABANDONED, NO_RESULT
}

enum class MatchFormat {
    T20, ODI, TEST, T10, HUNDRED, OTHER
}

data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val logoUrl: String? = null
)

data class LiveScore(
    val score: String,
    val runRate: String,
    val requiredRunRate: String? = null,
    val target: Int? = null,
    val recentBalls: List<String>? = null
)

data class MatchSummary(
    val id: String,
    val title: String,
    val state: MatchState,
    val format: MatchFormat,
    val team1: Team,
    val team2: Team,
    val team1Score: String?,
    val team2Score: String?,
    val status: String,
    val startTime: Long,
    val seriesName: String,
    val isLive: Boolean,
    val liveScore: LiveScore? = null
)

// ─── Match Details ───────────────────────────────────────────────────

data class MatchDetails(
    val summary: MatchSummary,
    val playerOfMatch: PlayerRef?,
    val currentBatsmen: List<BatsmanLive>,
    val currentBowlers: List<BowlerLive>,
    val partnership: String,
    val lastWicket: String,
    val recentBalls: String,
    val commentary: List<CommentaryEntry>
)

data class BatsmanLive(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val onStrike: Boolean
)

data class BowlerLive(
    val name: String,
    val overs: String,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Double
)

data class CommentaryEntry(
    val ball: String,
    val event: String,
    val text: String
)

// ─── Scorecard ───────────────────────────────────────────────────────

data class Scorecard(
    val matchId: String,
    val matchInfo: ScorecardMatchInfo,
    val status: String,
    val innings: List<InningsScorecard>
)

data class ScorecardMatchInfo(
    val title: String,
    val series: String,
    val venue: String,
    val dateTime: String
)

data class InningsScorecard(
    val inningsNumber: Int,
    val battingTeam: String,
    val score: String,
    val overs: String,
    val batting: List<BatsmanInnings>,
    val bowling: List<BowlerInnings>,
    val extras: String,
    val total: String,
    val didNotBat: List<String>,
    val fallOfWickets: List<FallOfWicket>,
    val powerplays: List<Powerplay>
)

data class BatsmanInnings(
    val name: String,
    val status: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double
)

data class BowlerInnings(
    val name: String,
    val overs: String,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val noBalls: Int,
    val wides: Int,
    val economy: Double
)

data class FallOfWicket(
    val player: String,
    val scoreAtWicket: String,
    val over: String
)

data class Powerplay(
    val type: String,
    val overs: String,
    val runs: Int
)

// ─── Squads ──────────────────────────────────────────────────────────

data class Squad(
    val matchId: String,
    val teams: Map<String, TeamSquad>
)

data class TeamSquad(
    val playingXI: List<PlayerRef>,
    val bench: List<PlayerRef>
)

data class PlayerRef(
    val id: String,
    val name: String,
    val role: String,
    val imageUrl: String? = null,
    val isCaptain: Boolean = false,
    val isKeeper: Boolean = false
)
