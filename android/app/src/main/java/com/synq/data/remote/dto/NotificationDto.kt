package com.synq.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SportsNotificationPrefsDto(
    val enabled: Boolean = true,
    val matchStartingSoon: Boolean = true,
    val matchStarted: Boolean = true,
    val matchConcluded: Boolean = true,
    val milestones: Boolean = true,
    val matchUpdates: Boolean = true,
)

@Serializable
data class NotificationPreferencesResponse(
    val preferences: SportsNotificationPrefsDto,
)

@Serializable
data class UpdateNotificationPreferencesRequest(
    val enabled: Boolean? = null,
    val matchStartingSoon: Boolean? = null,
    val matchStarted: Boolean? = null,
    val matchConcluded: Boolean? = null,
    val milestones: Boolean? = null,
    val matchUpdates: Boolean? = null,
)

@Serializable
data class RegisterFcmTokenRequest(
    val token: String,
    val platform: String = "android",
)

@Serializable
data class UnregisterFcmTokenRequest(
    val token: String,
)

@Serializable
data class RegisterFcmTokenResponse(
    val registered: Boolean,
)

@Serializable
data class UnregisterFcmTokenResponse(
    val removed: Boolean,
)
