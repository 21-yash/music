package com.synq.data.repository

import com.synq.core.domain.AppResult
import com.synq.data.local.TokenStorage
import com.synq.data.remote.api.AuthApi
import com.synq.data.remote.dto.*
import com.synq.data.remote.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication-related data.
 *
 * Implements the domain contract, coordinating between the remote API
 * and local token storage. Maps DTOs to domain models if needed (though
 * currently returning DTOs for simplicity).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
) {
    val isLoggedIn: Flow<Boolean> = tokenStorage.isLoggedIn
    val currentUserId: Flow<String?> = tokenStorage.userId
    val cachedUser: Flow<UserDto?> = tokenStorage.cachedUser

    suspend fun register(request: RegisterRequest): AppResult<AuthResult> {
        return safeApiCall { authApi.register(request) }
            .onSuccess { result ->
                tokenStorage.saveTokens(
                    accessToken = result.tokens.accessToken,
                    refreshToken = result.tokens.refreshToken,
                    userId = result.user.id
                )
                tokenStorage.saveUser(result.user)
            }
    }

    suspend fun login(request: LoginRequest): AppResult<AuthResult> {
        return safeApiCall { authApi.login(request) }
            .onSuccess { result ->
                tokenStorage.saveTokens(
                    accessToken = result.tokens.accessToken,
                    refreshToken = result.tokens.refreshToken,
                    userId = result.user.id
                )
                tokenStorage.saveUser(result.user)
            }
    }

    suspend fun getCurrentUser(): AppResult<UserDto> {
        val cached = tokenStorage.getCachedUserSync()
        return when (val remote = safeApiCall { authApi.getCurrentUser() }) {
            is AppResult.Success -> {
                tokenStorage.saveUser(remote.data)
                remote
            }
            is AppResult.Error -> {
                if (cached != null) {
                    AppResult.Success(cached)
                } else {
                    remote
                }
            }
        }
    }

    suspend fun logout(): AppResult<Unit?> {
        val refreshToken = tokenStorage.getRefreshTokenSync() ?: return AppResult.Success(Unit)
        
        return safeApiCall { authApi.logout(LogoutRequest(refreshToken)) }
            .onSuccess {
                tokenStorage.clearTokens()
            }
            .onError { 
                // Clear tokens locally even if server logout fails (e.g. network issue)
                tokenStorage.clearTokens()
            }
    }
}
