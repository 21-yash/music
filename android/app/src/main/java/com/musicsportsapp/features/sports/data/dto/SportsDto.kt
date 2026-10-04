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
    val title: String,
    val state: String,
    val format: String,
    val team1: TeamDto,
    val team2: TeamDto,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val status: String,
    val startTime: Long,
    val seriesName: String,
    val isLive: Boolean,
    val liveScore: LiveScoreDto? = null
) {
    fun toDomain(): MatchSummary {
        return MatchSummary(
            id = id,
            title = title,
            state = try { MatchState.valueOf(state.uppercase()) } catch (e: Exception) { MatchState.COMPLETED },
            format = try { MatchFormat.valueOf(format.uppercase()) } catch (e: Exception) { MatchFormat.OTHER },
            team1 = team1.toDomain(),
            team2 = team2.toDomain(),
            team1Score = team1Score,
            team2Score = team2Score,
            status = status,
            startTime = startTime,
            seriesName = seriesName,
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
    val logoUrl: String? = null
) {
    fun toDomain() = Team(id, name, shortName, logoUrl)
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
