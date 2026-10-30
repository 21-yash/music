package com.synq.features.music.jam

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamPreferences @Inject constructor(
    @ApplicationContext context: Context
) : JamPreferencesBridge {
    companion object {
        private const val PREFS_NAME = "synq_jam_prefs"
        private const val KEY_SEEN_BATTERY_TIP = "seen_battery_tip"
        private const val KEY_LAST_JAM_CODE = "last_jam_code"
        private const val KEY_LAST_JAM_TIMESTAMP = "last_jam_timestamp"
        private const val KEY_LAST_AVATAR_URL = "last_avatar_url"
        private const val KEY_REQUESTED_NOTIFICATION_PERMISSION = "requested_notification_permission"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hasSeenBatteryTip = MutableStateFlow(prefs.getBoolean(KEY_SEEN_BATTERY_TIP, false))
    val hasSeenBatteryTip: StateFlow<Boolean> = _hasSeenBatteryTip.asStateFlow()

    fun setSeenBatteryTip(seen: Boolean = true) {
        _hasSeenBatteryTip.value = seen
        prefs.edit().putBoolean(KEY_SEEN_BATTERY_TIP, seen).apply()
    }

    fun hasRequestedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_REQUESTED_NOTIFICATION_PERMISSION, false)

    fun setRequestedNotificationPermission(requested: Boolean = true) {
        prefs.edit().putBoolean(KEY_REQUESTED_NOTIFICATION_PERMISSION, requested).apply()
    }

    override fun saveLastJam(code: String, avatarUrl: String?) {
        val editor = prefs.edit()
            .putString(KEY_LAST_JAM_CODE, code)
            .putLong(KEY_LAST_JAM_TIMESTAMP, System.currentTimeMillis())
        if (avatarUrl != null) {
            editor.putString(KEY_LAST_AVATAR_URL, avatarUrl)
        }
        editor.apply()
    }

    override fun getLastAvatarUrl(): String? = prefs.getString(KEY_LAST_AVATAR_URL, null)

    fun getLastJamCodeIfRecent(maxAgeMs: Long = 2 * 60 * 60 * 1000L): String? {
        val code = prefs.getString(KEY_LAST_JAM_CODE, null) ?: return null
        val timestamp = prefs.getLong(KEY_LAST_JAM_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > maxAgeMs) {
            clearLastJam()
            return null
        }
        return code
    }

    override fun rejoinCandidate(): String? = getLastJamCodeIfRecent(2 * 60 * 60 * 1000L)

    override fun clearLastJam() {
        prefs.edit()
            .remove(KEY_LAST_JAM_CODE)
            .remove(KEY_LAST_JAM_TIMESTAMP)
            .remove(KEY_LAST_AVATAR_URL)
            .apply()
    }
}
