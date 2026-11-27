package com.synq.data.local

import android.content.Context
import android.content.SharedPreferences
import com.synq.data.remote.dto.SportsNotificationPrefsDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPreferencesLocal @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("synq_notification_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(readFromDisk())
    val preferences: StateFlow<SportsNotificationPrefsDto> = _preferences.asStateFlow()

    private fun readFromDisk(): SportsNotificationPrefsDto {
        return SportsNotificationPrefsDto(
            enabled = prefs.getBoolean("enabled", true),
            matchStartingSoon = prefs.getBoolean("matchStartingSoon", true),
            matchStarted = prefs.getBoolean("matchStarted", true),
            matchConcluded = prefs.getBoolean("matchConcluded", true),
            milestones = prefs.getBoolean("milestones", true),
            matchUpdates = prefs.getBoolean("matchUpdates", true),
        )
    }

    fun savePreferences(dto: SportsNotificationPrefsDto) {
        prefs.edit()
            .putBoolean("enabled", dto.enabled)
            .putBoolean("matchStartingSoon", dto.matchStartingSoon)
            .putBoolean("matchStarted", dto.matchStarted)
            .putBoolean("matchConcluded", dto.matchConcluded)
            .putBoolean("milestones", dto.milestones)
            .putBoolean("matchUpdates", dto.matchUpdates)
            .apply()
        _preferences.value = dto
    }

    fun getLastUploadedToken(): String? = prefs.getString("fcm_token", null)

    fun saveLastUploadedToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove("fcm_token")
            else putString("fcm_token", token)
        }.apply()
    }

    fun getEnabledBeforeSportsOff(): Boolean? {
        return if (prefs.contains("enabled_before_sports_off")) {
            prefs.getBoolean("enabled_before_sports_off", true)
        } else {
            null
        }
    }

    fun setEnabledBeforeSportsOff(wasEnabled: Boolean?) {
        prefs.edit().apply {
            if (wasEnabled == null) remove("enabled_before_sports_off")
            else putBoolean("enabled_before_sports_off", wasEnabled)
        }.apply()
    }
}
