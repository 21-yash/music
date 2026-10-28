package com.synq.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that intercepts HTTP 429 Too Many Requests responses,
 * extracts the Retry-After header, closes the response body to prevent socket leaks,
 * and throws [RateLimitException].
 */
@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 60L
            response.close() // Close response body to prevent socket leak
            throw RateLimitException(retryAfter)
        }
        return response
    }
}
