package com.musicsportsapp.data.remote.api

import com.musicsportsapp.data.remote.dto.*
import retrofit2.http.*

/**
 * Auth API endpoints.
 *
 * Maps to the backend's /api/v1/auth routes.
 * All methods return ApiResponse<T> — the interceptor handles
 * token injection and refresh.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthResult>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthResult>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<TokensDto>

    @GET("auth/me")
    suspend fun getCurrentUser(): ApiResponse<UserDto>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): ApiResponse<Unit?>

    @POST("auth/logout-all")
    suspend fun logoutAll(): ApiResponse<Unit?>

    @GET("auth/sessions")
    suspend fun getSessions(): ApiResponse<List<SessionDto>>

    @DELETE("auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): ApiResponse<Unit?>

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ApiResponse<Unit?>

    @POST("auth/delete-account")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): ApiResponse<Unit?>
}
