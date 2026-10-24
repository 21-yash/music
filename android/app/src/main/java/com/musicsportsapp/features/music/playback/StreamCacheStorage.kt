package com.musicsportsapp.features.music.playback

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
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveStreamUri(songId: String, uri: String) {
        prefs.edit().putString(songId, uri).apply()
    }

    fun getStreamUri(songId: String): String? {
        return prefs.getString(songId, null)
    }

    fun removeStreamUri(songId: String) {
        prefs.edit().remove(songId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
