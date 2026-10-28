package com.synq.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenRefresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Never attempt to authenticate public or auth-management routes (avoids recursion/deadlock)
        val path = response.request.url.encodedPath
        if (path.contains("auth/refresh") || path.contains("auth/login") || path.contains("auth/register")) {
            return null
        }

        // 2. If this response is already a retry that had a Bearer header, give up (avoid infinite loops)
        if (response.priorResponse != null &&
            response.priorResponse?.request?.header("Authorization")?.startsWith("Bearer ") == true
        ) {
            return null
        }

        // 3. Read the stale token directly from the failing request, not from storage
        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

        val freshToken = runBlocking { tokenRefresher.refresh(staleToken) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }
}

