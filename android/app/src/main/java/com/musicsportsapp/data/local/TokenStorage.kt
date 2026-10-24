package com.musicsportsapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.musicsportsapp.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Secure token storage using DataStore.
 *
 * Stores access token, refresh token, and userId so the app can
 * restore auth state across app restarts and perform token refresh.
 *
 * Note: For production, consider EncryptedSharedPreferences or
 * the Security Crypto library. DataStore is sufficient for the
 * current phase.
 */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("user_name")
        private val KEY_EMAIL = stringPreferencesKey("user_email")
        private val KEY_CREATED_AT = stringPreferencesKey("user_created_at")
        private val KEY_UPDATED_AT = stringPreferencesKey("user_updated_at")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ACCESS_TOKEN] != null && it[KEY_USER_ID] != null
    }

    val cachedUser: Flow<com.musicsportsapp.data.remote.dto.UserDto?> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID]
        val username = prefs[KEY_USERNAME]
        val email = prefs[KEY_EMAIL]
        if (id != null && username != null && email != null) {
            com.musicsportsapp.data.remote.dto.UserDto(
                id = id,
                username = username,
                email = email,
                createdAt = prefs[KEY_CREATED_AT] ?: "",
                updatedAt = prefs[KEY_UPDATED_AT] ?: "",
            )
        } else {
            null
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun saveUser(user: com.musicsportsapp.data.remote.dto.UserDto) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_EMAIL] = user.email
            prefs[KEY_CREATED_AT] = user.createdAt
            prefs[KEY_UPDATED_AT] = user.updatedAt
        }
    }

    suspend fun updateAccessToken(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_CREATED_AT)
            prefs.remove(KEY_UPDATED_AT)
        }
    }

    suspend fun getAccessTokenSync(): String? = context.dataStore.data.first()[KEY_ACCESS_TOKEN]
    suspend fun getRefreshTokenSync(): String? = context.dataStore.data.first()[KEY_REFRESH_TOKEN]
    suspend fun getUserIdSync(): String? = context.dataStore.data.first()[KEY_USER_ID]

    suspend fun getCachedUserSync(): com.musicsportsapp.data.remote.dto.UserDto? {
        val prefs = context.dataStore.data.first()
        val id = prefs[KEY_USER_ID] ?: return null
        val username = prefs[KEY_USERNAME] ?: return null
        val email = prefs[KEY_EMAIL] ?: return null
        return com.musicsportsapp.data.remote.dto.UserDto(
            id = id,
            username = username,
            email = email,
            createdAt = prefs[KEY_CREATED_AT] ?: "",
            updatedAt = prefs[KEY_UPDATED_AT] ?: "",
        )
    }
}
