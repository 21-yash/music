package com.synq.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages whether Cricket/Sports features are enabled across the app.
 *
 * Uses SharedPreferences so the preference is read synchronously at cold start
 * without bottom-bar or home-section flicker before the first frame renders.
 */
@Singleton
class SportsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "synq_sports_prefs"
        private const val KEY_IS_SPORTS_ENABLED = "is_sports_enabled"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSportsEnabled = MutableStateFlow(prefs.getBoolean(KEY_IS_SPORTS_ENABLED, true))
    val isSportsEnabled: StateFlow<Boolean> = _isSportsEnabled.asStateFlow()

    fun setSportsEnabled(enabled: Boolean) {
        _isSportsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_IS_SPORTS_ENABLED, enabled).apply()
    }

    fun toggleSports() {
        setSportsEnabled(!_isSportsEnabled.value)
    }
}
