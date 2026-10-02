package com.musicsportsapp.core.domain

/**
 * A generic result wrapper for domain/repository operations.
 *
 * Replaces throwing exceptions for expected error cases (network errors,
 * validation errors, auth failures, etc.). Unexpected exceptions should
 * still be thrown.
 *
 * Design decision: this is intentionally a simple sealed class rather
 * than using kotlin.Result, because kotlin.Result cannot carry typed
 * error information and has restrictions on usage in public APIs.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Error)?.error

    /**
     * Map the success value, preserving errors.
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /**
     * Execute a block on success, returning the original result.
     */
    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    /**
     * Execute a block on error, returning the original result.
     */
    inline fun onError(block: (AppError) -> Unit): AppResult<T> {
        if (this is Error) block(error)
        return this
    }
}

/**
 * Typed application errors.
 *
 * These map to the backend's error codes but are Android-friendly —
 * they carry user-presentable messages and machine-readable codes.
 */
sealed class AppError(
    val code: String,
    val message: String,
) {
    /** Network is unreachable */
    class Network(message: String = "No internet connection") :
        AppError("NETWORK_ERROR", message)

    /** Server returned an error response */
    class Server(code: String, message: String) :
        AppError(code, message)

    /** Authentication failed or token expired */
    class Unauthorized(message: String = "Authentication required") :
        AppError("UNAUTHORIZED", message)

    /** Resource not found */
    class NotFound(message: String = "Not found") :
        AppError("NOT_FOUND", message)

    /** Validation error */
    class Validation(message: String = "Validation failed") :
        AppError("VALIDATION_ERROR", message)

    /** Generic unexpected error */
    class Unknown(message: String = "An unexpected error occurred") :
        AppError("UNKNOWN", message)
}
