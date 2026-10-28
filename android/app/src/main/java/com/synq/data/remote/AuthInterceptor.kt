package com.synq.data.remote

import com.synq.BuildConfig
import com.synq.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches:
 * 1. The X-Api-Key header to every request.
 * 2. The Bearer access token to non-public endpoints.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {

    companion object {
        /** Paths that don't need a user Authorization header */
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

        val builder = request.newBuilder()

        // Always add X-Api-Key
        builder.header("X-Api-Key", BuildConfig.API_KEY)

        // Add Authorization header only for non-public paths
        val isPublic = PUBLIC_PATHS.any { path.contains(it) }
        if (!isPublic) {
            val token = runBlocking { tokenStorage.getAccessTokenSync() }
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
