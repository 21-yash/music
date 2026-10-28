package com.synq.data.remote

import com.synq.data.local.TokenStorage
import com.synq.data.remote.api.AuthApi
import com.synq.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TokenRefresher @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authApiProvider: Provider<AuthApi>,
) {
    private val mutex = Mutex()

    /**
     * Refreshes the access token in a thread-safe and coroutine-safe manner.
     * Prevents rotation races by sharing a single lock between OkHttp AuthAuthenticator and WebSockets.
     *
     * @param staleAccessToken The access token that the caller detected as stale (null if unknown)
     * @return Fresh access token, or null if refresh failed
     */
    suspend fun refresh(staleAccessToken: String? = null): String? {
        mutex.withLock {
            val currentAccessToken = tokenStorage.getAccessTokenSync()
            val currentRefreshToken = tokenStorage.getRefreshTokenSync()
            val userId = tokenStorage.getUserIdSync()

            // 1. If stored access token changed while waiting for lock, another caller already refreshed
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != staleAccessToken) {
                return currentAccessToken
            }

            if (currentRefreshToken.isNullOrBlank() || userId.isNullOrBlank()) {
                return null
            }

            return try {
                val authApi = authApiProvider.get()
                val response = authApi.refreshToken(RefreshTokenRequest(userId, currentRefreshToken))

                if (response.success && response.data != null) {
                    val newTokens = response.data
                    tokenStorage.saveTokens(newTokens.accessToken, newTokens.refreshToken, userId)
                    newTokens.accessToken
                } else {
                    // Check if refresh token changed during the network call (rotation race)
                    val freshRefreshToken = tokenStorage.getRefreshTokenSync()
                    if (freshRefreshToken != null && freshRefreshToken != currentRefreshToken) {
                        return tokenStorage.getAccessTokenSync()
                    }
                    // 2xx response with success=false: non-clearing, keep tokens
                    null
                }
            } catch (e: Exception) {
                // Check if refresh token changed during the network call (rotation race)
                val freshRefreshToken = tokenStorage.getRefreshTokenSync()
                if (freshRefreshToken != null && freshRefreshToken != currentRefreshToken) {
                    return tokenStorage.getAccessTokenSync()
                }

                // ONLY clear tokens when server definitively rejects with HTTP 401 Unauthorized
                if (e is HttpException && e.code() == 401) {
                    tokenStorage.clearTokens()
                }

                // For any other HttpException (400, 403, 429, 5xx), IOException,
                // serialization errors, or other exceptions: keep tokens and return null
                null
            }
        }
    }
}
