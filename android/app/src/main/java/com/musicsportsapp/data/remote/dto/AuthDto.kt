package com.musicsportsapp.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Auth-related DTOs for the /api/v1/auth endpoints.
 */

// ─── Requests ─────────────────────────────────────────────────────────

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val deviceName: String? = null,
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
    val deviceName: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    val userId: String,
    val refreshToken: String,
    val deviceName: String? = null,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class DeleteAccountRequest(
    val password: String,
)

// ─── Responses ────────────────────────────────────────────────────────

@Serializable
data class AuthResult(
    val user: UserDto,
    val tokens: TokensDto,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val lastLogin: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class SessionDto(
    val id: String,
    val deviceName: String? = null,
    val createdAt: String,
    val expiresAt: String,
    val isCurrent: Boolean,
)
