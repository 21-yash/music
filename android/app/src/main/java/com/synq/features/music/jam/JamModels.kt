package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song

data class JamParticipantUi(
    val socketId: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val isHost: Boolean = false
)

data class JamState(
    val isInJam: Boolean = false,
    val sessionCode: String? = null,
    val isHost: Boolean = false,
    val hostUserId: String = "",
    val hostName: String = "",
    val allowAttendeeReorder: Boolean = false,
    val isLocalStepOut: Boolean = false,
    val isLocalPlaybackUnplayable: Boolean = false,
    val participants: List<JamParticipantUi> = emptyList(),
    val queue: List<Song> = emptyList(),
    val error: String? = null
)

val LocalIsInJam = androidx.compose.runtime.compositionLocalOf { false }
val LocalCanAddSongsToJam = androidx.compose.runtime.compositionLocalOf { true }
