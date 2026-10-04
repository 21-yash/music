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
