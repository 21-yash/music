package com.synq.data.remote

import com.synq.BuildConfig
import com.synq.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Task H7: Pure function to check whether an access token is expired or within skew window.
 * Decodes the JWT payload locally (base64url) without full cryptographic verification.
 * Returns true if expired or expiring within [skewMs].
 * Returns false if valid or if the token cannot be parsed (so callers can fall back to standard behavior).
 */
fun isAccessTokenExpiring(
    token: String?,
    nowMs: Long,
    skewMs: Long = 30_000L
): Boolean {
    if (token.isNullOrBlank()) return false
    val parts = token.split(".")
    if (parts.size < 2) return false

    return try {
        val payloadBase64 = parts[1]
        val decodedBytes = try {
            Base64.getUrlDecoder().decode(payloadBase64)
        } catch (_: Throwable) {
            android.util.Base64.decode(
                payloadBase64,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        }
        val jsonString = String(decodedBytes, Charsets.UTF_8)
        val json = JSONObject(jsonString)
        if (!json.has("exp")) return false

        val expSec = json.getLong("exp")
        val expMs = expSec * 1000L
        (expMs - nowMs) <= skewMs
    } catch (_: Throwable) {
        false
    }
}

/**
 * OkHttp interceptor that attaches:
 * 1. The X-Api-Key header to every request.
 * 2. The Bearer access token to non-public endpoints.
 * Proactively refreshes tokens expiring within 30s to avoid 401 round trips.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val tokenRefresher: TokenRefresher,
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
            var token = runBlocking { tokenStorage.getAccessTokenSync() }
            if (token != null && isAccessTokenExpiring(token, System.currentTimeMillis(), 30_000L)) {
                val refreshResult = runBlocking { tokenRefresher.refreshResult(token) }
                if (refreshResult is TokenRefreshResult.Success) {
                    token = refreshResult.token
                }
            }
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
