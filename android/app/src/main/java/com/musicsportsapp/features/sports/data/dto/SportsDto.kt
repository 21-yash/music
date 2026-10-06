package com.musicsportsapp.features.sports.data.dto

import com.musicsportsapp.features.sports.domain.model.LiveScore
import com.musicsportsapp.features.sports.domain.model.MatchFormat
import com.musicsportsapp.features.sports.domain.model.MatchState
import com.musicsportsapp.features.sports.domain.model.MatchSummary
import com.musicsportsapp.features.sports.domain.model.Team
import kotlinx.serialization.Serializable

@Serializable
data class MatchesResponseDto(
    val matches: List<MatchSummaryDto>,
    val fetchedAt: String
)

@Serializable
data class MatchSummaryDto(
    val id: String,
    val series: String = "",
    val matchDesc: String = "",
    val venue: String = "",
    val state: String,
    val format: String = "OTHER",
    val team1: TeamDto,
    val team2: TeamDto,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val status: String = "",
    val startTimestamp: Long = 0,
    val isLive: Boolean = false,
    val liveScore: LiveScoreDto? = null
) {
    fun toDomain(): MatchSummary {
        val computedTitle = if (series.isNotEmpty() && matchDesc.isNotEmpty()) {
            "$series - $matchDesc"
        } else {
            "${team1.name} vs ${team2.name}"
        }

        return MatchSummary(
            id = id,
            title = computedTitle,
            matchDesc = matchDesc,
            series = series,
            venue = venue,
            state = try { MatchState.valueOf(state.uppercase()) } catch (e: Exception) { MatchState.COMPLETED },
            format = try { MatchFormat.valueOf(format.uppercase()) } catch (e: Exception) { MatchFormat.OTHER },
            team1 = team1.toDomain(),
            team2 = team2.toDomain(),
            team1Score = team1Score,
            team2Score = team2Score,
            status = status,
            startTime = startTimestamp,
            seriesName = series,
            isLive = isLive,
            liveScore = liveScore?.toDomain()
        )
    }
}

@Serializable
data class TeamDto(
    val id: String,
    val name: String,
    val shortName: String,
    val flagUrl: String? = null
) {
    fun toDomain() = Team(id, name, shortName, flagUrl)
}

@Serializable
data class LiveScoreDto(
    val score: String,
    val runRate: String,
    val requiredRunRate: String? = null,
    val target: Int? = null,
    val recentBalls: List<String>? = null
) {
    fun toDomain() = LiveScore(score, runRate, requiredRunRate, target, recentBalls)
}

// ─── Match Details ───────────────────────────────────────────────────

@Serializable
data class MatchDetailsDto(
    val id: String,
    val series: String = "",
    val matchDesc: String = "",
    val venue: String = "",
    val state: String,
    val format: String = "OTHER",
    val team1: TeamDto,
    val team2: TeamDto,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val status: String = "",
    val startTimestamp: Long = 0,
    val isLive: Boolean = false,
    val liveScore: LiveScoreDto? = null,
    val playerOfMatch: PlayerRefDto? = null,
    val currentBatsmen: List<BatsmanLiveDto> = emptyList(),
    val currentBowlers: List<BowlerLiveDto> = emptyList(),
    val partnership: String = "",
    val lastWicket: String = "",
    val recentBalls: String = "",
    val toss: String = "",
    val oversLeft: String = "",
    val latestPerformance: List<PerformanceDto> = emptyList(),
    val commentary: List<CommentaryEntryDto> = emptyList()
) {
    fun toDomain(): com.musicsportsapp.features.sports.domain.model.MatchDetails {
        val summaryDto = MatchSummaryDto(
            id = id,
            series = series,
            matchDesc = matchDesc,
            venue = venue,
            state = state,
            format = format,
            team1 = team1,
            team2 = team2,
            team1Score = team1Score,
            team2Score = team2Score,
            status = status,
            startTimestamp = startTimestamp,
            isLive = isLive,
            liveScore = liveScore
        )
        return com.musicsportsapp.features.sports.domain.model.MatchDetails(
            summary = summaryDto.toDomain(),
            playerOfMatch = playerOfMatch?.toDomain(),
            currentBatsmen = currentBatsmen.map { it.toDomain() },
            currentBowlers = currentBowlers.map { it.toDomain() },
            partnership = partnership,
            lastWicket = lastWicket,
            recentBalls = recentBalls,
            toss = toss,
            oversLeft = oversLeft,
            latestPerformance = latestPerformance.map { it.toDomain() },
            commentary = commentary.map { it.toDomain() }
        )
    }
}

@Serializable
data class PerformanceDto(
    val runs: Int = 0,
    val wkts: Int = 0,
    val label: String = ""
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.Performance(runs, wkts, label)
}

@Serializable
data class BatsmanLiveDto(
    val name: String,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val strikeRate: Double = 0.0,
    val onStrike: Boolean = false
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.BatsmanLive(
        name, runs, balls, fours, sixes, strikeRate, onStrike
    )
}

@Serializable
data class BowlerLiveDto(
    val name: String,
    val overs: String = "0",
    val maidens: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val economy: Double = 0.0
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.BowlerLive(
        name, overs, maidens, runs, wickets, economy
    )
}

@Serializable
data class CommentaryEntryDto(
    val ball: String = "",
    val event: String = "",
    val text: String
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.CommentaryEntry(
        ball, event, text
    )
}

// ─── Scorecard ───────────────────────────────────────────────────────

@Serializable
data class ScorecardDto(
    val matchId: String,
    val matchInfo: ScorecardMatchInfoDto,
    val status: String = "",
    val innings: List<InningsScorecardDto> = emptyList()
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.Scorecard(
        matchId, matchInfo.toDomain(), status, innings.map { it.toDomain() }
    )
}

@Serializable
data class ScorecardMatchInfoDto(
    val title: String = "",
    val series: String = "",
    val venue: String = "",
    val dateTime: String = ""
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.ScorecardMatchInfo(
        title, series, venue, dateTime
    )
}

@Serializable
data class InningsScorecardDto(
    val inningsNumber: Int,
    val battingTeam: String = "",
    val score: String = "",
    val overs: String = "",
    val batting: List<BatsmanInningsDto> = emptyList(),
    val bowling: List<BowlerInningsDto> = emptyList(),
    val extras: String = "",
    val total: String = "",
    val didNotBat: List<String> = emptyList(),
    val fallOfWickets: List<FallOfWicketDto> = emptyList(),
    val powerplays: List<PowerplayDto> = emptyList()
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.InningsScorecard(
        inningsNumber, battingTeam, score, overs, 
        batting.map { it.toDomain() }, bowling.map { it.toDomain() },
        extras, total, didNotBat, fallOfWickets.map { it.toDomain() }, powerplays.map { it.toDomain() }
    )
}

@Serializable
data class BatsmanInningsDto(
    val name: String,
    val status: String = "",
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val strikeRate: Double = 0.0
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.BatsmanInnings(
        name, status, runs, balls, fours, sixes, strikeRate
    )
}

@Serializable
data class BowlerInningsDto(
    val name: String,
    val overs: String = "0",
    val maidens: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val noBalls: Int = 0,
    val wides: Int = 0,
    val economy: Double = 0.0
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.BowlerInnings(
        name, overs, maidens, runs, wickets, noBalls, wides, economy
    )
}

@Serializable
data class FallOfWicketDto(
    val player: String,
    val scoreAtWicket: String = "",
    val over: String = ""
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.FallOfWicket(
        player, scoreAtWicket, over
    )
}

@Serializable
data class PowerplayDto(
    val type: String,
    val overs: String = "",
    val runs: Int = 0
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.Powerplay(
        type, overs, runs
    )
}

// ─── Squads ──────────────────────────────────────────────────────────

@Serializable
data class SquadDto(
    val matchId: String,
    val teams: Map<String, TeamSquadDto> = emptyMap()
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.Squad(
        matchId, teams.mapValues { it.value.toDomain() }
    )
}

@Serializable
data class SquadResponseDto(
    val squads: SquadDto,
    val fetchedAt: String
)

@Serializable
data class MatchDetailsResponseDto(
    val match: MatchDetailsDto,
    val fetchedAt: String
)

@Serializable
data class ScorecardResponseDto(
    val scorecard: ScorecardDto,
    val fetchedAt: String
)

@Serializable
data class TeamSquadDto(
    val playingXI: List<PlayerRefDto> = emptyList(),
    val bench: List<PlayerRefDto> = emptyList()
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.TeamSquad(
        playingXI.map { it.toDomain() }, bench.map { it.toDomain() }
    )
}

@Serializable
data class PlayerRefDto(
    val id: String,
    val name: String,
    val role: String = "",
    val imageUrl: String? = null,
    val isCaptain: Boolean = false,
    val isKeeper: Boolean = false
) {
    fun toDomain() = com.musicsportsapp.features.sports.domain.model.PlayerRef(
        id, name, role, imageUrl, isCaptain, isKeeper
    )
}
