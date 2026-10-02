package com.musicsportsapp.data.remote

import com.musicsportsapp.core.domain.AppError
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.remote.dto.ApiResponse
import retrofit2.HttpException
import java.io.IOException

/**
 * Safely execute a Retrofit API call and map the result to [AppResult].
 *
 * Handles:
 * - Network errors (no connectivity) → AppError.Network
 * - HTTP errors → parsed from the API response body if possible
 * - Backend error responses (success=false) → AppError.Server
 * - Unexpected exceptions → AppError.Unknown
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> ApiResponse<T>,
): AppResult<T> {
    return try {
        val response = apiCall()

        if (response.success && response.data != null) {
            AppResult.Success(response.data)
        } else if (response.error != null) {
            val error = response.error
            when (error.code) {
                "UNAUTHORIZED" -> AppResult.Error(AppError.Unauthorized(error.message))
                "NOT_FOUND" -> AppResult.Error(AppError.NotFound(error.message))
                "VALIDATION_ERROR" -> AppResult.Error(AppError.Validation(error.message))
                else -> AppResult.Error(AppError.Server(error.code, error.message))
            }
        } else {
            // success=true but data is null — valid for void endpoints
            @Suppress("UNCHECKED_CAST")
            AppResult.Success(Unit as T)
        }
    } catch (e: IOException) {
        AppResult.Error(AppError.Network())
    } catch (e: HttpException) {
        AppResult.Error(
            AppError.Server(
                code = "HTTP_${e.code()}",
                message = e.message() ?: "HTTP error ${e.code()}",
            )
        )
    } catch (e: Exception) {
        AppResult.Error(AppError.Unknown(e.message ?: "An unexpected error occurred"))
    }
}
