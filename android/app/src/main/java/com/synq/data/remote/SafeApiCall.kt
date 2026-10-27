package com.synq.data.remote

import com.synq.core.domain.AppError
import com.synq.core.domain.AppResult
import com.synq.data.remote.dto.ApiResponse
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
        val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        var errorCode = "HTTP_${e.code()}"
        var errorMessage = if (e.message().isNotBlank()) e.message() else "HTTP error ${e.code()}"

        if (!errorBody.isNullOrBlank()) {
            try {
                val jsonObj = org.json.JSONObject(errorBody)
                if (jsonObj.has("error")) {
                    val errObj = jsonObj.getJSONObject("error")
                    if (errObj.has("code")) errorCode = errObj.getString("code")
                    if (errObj.has("message")) errorMessage = errObj.getString("message")
                } else if (jsonObj.has("message")) {
                    errorMessage = jsonObj.getString("message")
                }
            } catch (_: Exception) {
                val codeMatch = """"code"\s*:\s*"([^"]+)"""".toRegex().find(errorBody)
                if (codeMatch != null) errorCode = codeMatch.groupValues[1]
                val msgMatch = """"message"\s*:\s*"([^"]+)"""".toRegex().find(errorBody)
                if (msgMatch != null) errorMessage = msgMatch.groupValues[1]
            }
        }

        val friendlyMessage = errorMessage
            .replace(Regex("""^[a-zA-Z0-9_.]*:\s*"""), "")
            .replace(Regex(""";\s*[a-zA-Z0-9_.]*:\s*"""), "; ")

        when (errorCode) {
            "UNAUTHORIZED" -> AppResult.Error(AppError.Unauthorized(friendlyMessage))
            "NOT_FOUND" -> AppResult.Error(AppError.NotFound(friendlyMessage))
            "VALIDATION_ERROR" -> AppResult.Error(AppError.Validation(friendlyMessage))
            else -> AppResult.Error(AppError.Server(errorCode, friendlyMessage))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Error(AppError.Unknown(e.message ?: "An unexpected error occurred"))
    }
}
