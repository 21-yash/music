package com.synq.di

import com.synq.data.remote.AuthAuthenticator
import com.synq.data.remote.AuthInterceptor
import com.synq.data.remote.RateLimitInterceptor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `provideOkHttpClient configures 30s connect timeout`() {
        val authInterceptor = mockk<AuthInterceptor>(relaxed = true)
        val authAuthenticator = mockk<AuthAuthenticator>(relaxed = true)
        val rateLimitInterceptor = mockk<RateLimitInterceptor>(relaxed = true)

        val client = NetworkModule.provideOkHttpClient(
            authInterceptor = authInterceptor,
            authAuthenticator = authAuthenticator,
            rateLimitInterceptor = rateLimitInterceptor
        )

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }
}

