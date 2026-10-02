package com.musicsportsapp.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Standard API response wrappers matching the backend contract.
 *
 * Success: { "success": true, "data": T, "message": string | null }
 * Error:   { "success": false, "error": { "code": "...", "message": "..." } }
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)
