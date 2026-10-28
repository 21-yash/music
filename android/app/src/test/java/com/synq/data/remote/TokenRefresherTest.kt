package com.synq.data.remote

import com.synq.data.local.TokenStorage
import com.synq.data.remote.api.AuthApi
import com.synq.data.remote.dto.ApiResponse
import com.synq.data.remote.dto.ApiError
import com.synq.data.remote.dto.TokensDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Provider

class TokenRefresherTest {

    private val tokenStorage: TokenStorage = mockk(relaxed = true)
    private val authApi: AuthApi = mockk()
    private val authApiProvider: Provider<AuthApi> = Provider { authApi }

    private lateinit var tokenRefresher: TokenRefresher

    @Before
    fun setup() {
        tokenRefresher = TokenRefresher(tokenStorage, authApiProvider)
        coEvery { tokenStorage.getAccessTokenSync() } returns "old_access_token"
        coEvery { tokenStorage.getRefreshTokenSync() } returns "old_refresh_token"
        coEvery { tokenStorage.getUserIdSync() } returns "user_123"
    }

    private fun createHttpException(code: Int): HttpException {
        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        val response = Response.error<TokensDto>(code, errorBody)
        return HttpException(response)
    }

    @Test
    fun testHttp401ClearsTokens() = runTest {
        coEvery { authApi.refreshToken(any()) } throws createHttpException(401)

        val result = tokenRefresher.refresh("old_access_token")

        assertNull(result)
        coVerify(exactly = 1) { tokenStorage.clearTokens() }
    }

    @Test
    fun testHttp500DoesNotClearTokens() = runTest {
        coEvery { authApi.refreshToken(any()) } throws createHttpException(500)

        val result = tokenRefresher.refresh("old_access_token")

        assertNull(result)
        coVerify(exactly = 0) { tokenStorage.clearTokens() }
    }

    @Test
    fun testSerializationErrorDoesNotClearTokens() = runTest {
        coEvery { authApi.refreshToken(any()) } throws kotlinx.serialization.SerializationException("Malformed JSON")

        val result = tokenRefresher.refresh("old_access_token")

        assertNull(result)
        coVerify(exactly = 0) { tokenStorage.clearTokens() }
    }

    @Test
    fun testRotationRaceReturnsNewTokenWithoutClearing() = runTest {
        // While authApi.refreshToken is called, simulate another caller refreshing in storage
        coEvery { authApi.refreshToken(any()) } answers {
            coEvery { tokenStorage.getRefreshTokenSync() } returns "fresh_refresh_token"
            coEvery { tokenStorage.getAccessTokenSync() } returns "fresh_access_token"
            throw createHttpException(401) // Server rejected the old token that was already consumed
        }

        val result = tokenRefresher.refresh("old_access_token")

        assertEquals("fresh_access_token", result)
        coVerify(exactly = 0) { tokenStorage.clearTokens() }
    }

    @Test
    fun test2xxResponseWithSuccessFalseDoesNotClearTokens() = runTest {
        coEvery { authApi.refreshToken(any()) } returns ApiResponse(
            success = false,
            data = null,
            error = ApiError("INVALID_REFRESH", "Invalid refresh request")
        )

        val result = tokenRefresher.refresh("old_access_token")

        assertNull(result)
        coVerify(exactly = 0) { tokenStorage.clearTokens() }
    }

    @Test
    fun testSuccessfulRefreshSavesTokensAndReturnsAccessToken() = runTest {
        coEvery { authApi.refreshToken(any()) } returns ApiResponse(
            success = true,
            data = TokensDto(accessToken = "new_access_token", refreshToken = "new_refresh_token"),
            error = null
        )

        val result = tokenRefresher.refresh("old_access_token")

        assertEquals("new_access_token", result)
        coVerify(exactly = 1) { tokenStorage.saveTokens("new_access_token", "new_refresh_token", "user_123") }
        coVerify(exactly = 0) { tokenStorage.clearTokens() }
    }
}
