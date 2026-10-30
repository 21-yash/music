package com.synq.features.music.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight persistent storage for resolved stream URIs.
 * Allows instant offline playback from ExoPlayer's SimpleCache without
 * needing a network call to the backend to resolve the stream URL again.
 */
@Singleton
class StreamCacheStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "stream_cache_urls"
        const val DEFAULT_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours TTL for CDN stream URLs
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveStreamUri(songId: String, uri: String, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putString(songId, "$timestamp|$uri").apply()
    }

    fun getStreamUri(songId: String, maxAgeMs: Long = DEFAULT_TTL_MS): String? {
        val entry = prefs.getString(songId, null) ?: return null
        val delimiter = entry.indexOf('|')
        if (delimiter == -1) {
            // Legacy entry without timestamp: invalidate
            removeStreamUri(songId)
            return null
        }
        val timestamp = entry.substring(0, delimiter).toLongOrNull() ?: run {
            removeStreamUri(songId)
            return null
        }
        if (System.currentTimeMillis() - timestamp > maxAgeMs) {
            removeStreamUri(songId)
            return null
        }
        return entry.substring(delimiter + 1)
    }

    fun invalidateStreamUri(songId: String) {
        removeStreamUri(songId)
    }

    fun removeStreamUri(songId: String) {
        prefs.edit().remove(songId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
