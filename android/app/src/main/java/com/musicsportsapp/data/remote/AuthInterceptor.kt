package com.musicsportsapp.data.remote

import com.musicsportsapp.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches the Bearer access token to
 * every request (unless the request is to a public auth endpoint).
 *
 * Uses runBlocking to read the token synchronously from DataStore,
 * which is acceptable in an OkHttp interceptor since it runs on
 * OkHttp's own thread pool (not the main thread).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {

    companion object {
        /** Paths that don't need an auth header */
        private val PUBLIC_PATHS = listOf(
            "auth/register",
            "auth/login",
            "auth/refresh",
            "health",
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Skip auth header for public endpoints
        if (PUBLIC_PATHS.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenStorage.getAccessTokenSync() }

        return if (token != null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}
