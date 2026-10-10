package com.musicsportsapp.data.remote

import com.musicsportsapp.data.local.TokenStorage
import com.musicsportsapp.data.remote.api.AuthApi
import com.musicsportsapp.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authApiProvider: Provider<AuthApi>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val token = runBlocking { tokenStorage.getAccessTokenSync() }

        synchronized(this) {
            val currentToken = runBlocking { tokenStorage.getAccessTokenSync() }
            if (currentToken != null && currentToken != token) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { tokenStorage.getRefreshTokenSync() } ?: return null
            val userId = runBlocking { tokenStorage.getUserIdSync() } ?: return null
            
            return try {
                val authApi = authApiProvider.get()
                val refreshResponse = runBlocking { 
                    authApi.refreshToken(RefreshTokenRequest(userId, refreshToken))
                }
                
                if (refreshResponse.success && refreshResponse.data != null) {
                    val newTokens = refreshResponse.data
                    runBlocking { 
                        tokenStorage.saveTokens(newTokens.accessToken, newTokens.refreshToken, userId)
                    }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                } else {
                    runBlocking { tokenStorage.clearTokens() }
                    null
                }
            } catch (e: Exception) {
                runBlocking { tokenStorage.clearTokens() }
                null
            }
        }
    }
}
