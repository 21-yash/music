package com.musicsportsapp.features.search

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages locally stored recent music searches (up to 10 entries).
 * Persisted exclusively in private SharedPreferences.
 */
@Singleton
class RecentSearchManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_recent_searches_prefs", Context.MODE_PRIVATE)
    private val key = "recent_searches_list"

    fun getRecentSearches(): List<String> {
        val jsonStr = prefs.getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSearch(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getRecentSearches()

        val current = getRecentSearches().toMutableList()
        // Deduplicate case-insensitively
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        // Prepend latest search to top
        current.add(0, trimmed)
        // Store only the last 10 searches locally
        val updated = current.take(10)

        save(updated)
        return updated
    }

    fun removeSearch(query: String): List<String> {
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        save(current)
        return current
    }

    fun clearAll(): List<String> {
        prefs.edit().remove(key).apply()
        return emptyList()
    }

    private fun save(list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }
}
