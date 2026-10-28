package com.synq.data.remote

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthAuthenticatorTest {

    private val tokenRefresher: TokenRefresher = mockk(relaxed = true)
    private val authenticator = AuthAuthenticator(tokenRefresher)

    @Test
    fun test401FromAuthRefreshDoesNotCallTokenRefresher() {
        val request = Request.Builder()
            .url("https://api.example.com/api/v1/auth/refresh")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenRefresher.refresh(any()) }
    }

    @Test
    fun test401FromAuthLoginDoesNotCallTokenRefresher() {
        val request = Request.Builder()
            .url("https://api.example.com/api/v1/auth/login")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenRefresher.refresh(any()) }
    }

    @Test
    fun test401FromAuthRegisterDoesNotCallTokenRefresher() {
        val request = Request.Builder()
            .url("https://api.example.com/api/v1/auth/register")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenRefresher.refresh(any()) }
    }

    @Test
    fun testResponseWithPriorBearerResponseReturnsNull() {
        val priorRequest = Request.Builder()
            .url("https://api.example.com/api/v1/music/trending")
            .header("Authorization", "Bearer stale_token")
            .build()
        val priorResponse = Response.Builder()
            .request(priorRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = Request.Builder()
            .url("https://api.example.com/api/v1/music/trending")
            .header("Authorization", "Bearer fresh_token")
            .build()
        val response = Response.Builder()
            .request(retryRequest)
            .priorResponse(priorResponse)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenRefresher.refresh(any()) }
    }

    @Test
    fun testNormal401PassesStaleTokenFromHeader() {
        coEvery { tokenRefresher.refresh("stale_abc") } returns "fresh_xyz"

        val request = Request.Builder()
            .url("https://api.example.com/api/v1/music/trending")
            .header("Authorization", "Bearer stale_abc")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer fresh_xyz", result?.header("Authorization"))
        coVerify(exactly = 1) { tokenRefresher.refresh("stale_abc") }
    }
}
