package com.synq.features.music.playback

import com.synq.core.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRetryPolicyTest {

    @Test
    fun testNetworkRetriesTwiceThenStops() {
        val error = AppError.Network("No internet")

        val attempt1 = StreamRetryPolicy.decide(error, attempt = 1)
        assertTrue(attempt1 is StreamRetryDecision.Retry)
        assertEquals(400L, (attempt1 as StreamRetryDecision.Retry).delayMs)

        val attempt2 = StreamRetryPolicy.decide(error, attempt = 2)
        assertTrue(attempt2 is StreamRetryDecision.Retry)
        assertEquals(1200L, (attempt2 as StreamRetryDecision.Retry).delayMs)

        val attempt3 = StreamRetryPolicy.decide(error, attempt = 3)
        assertTrue(attempt3 is StreamRetryDecision.GiveUp)
    }

    @Test
    fun test5xxRetriesTwiceThenStops() {
        val error = AppError.Server("500", "Internal Server Error")

        val attempt1 = StreamRetryPolicy.decide(error, attempt = 1)
        assertTrue(attempt1 is StreamRetryDecision.Retry)
        assertEquals(400L, (attempt1 as StreamRetryDecision.Retry).delayMs)

        val attempt2 = StreamRetryPolicy.decide(error, attempt = 2)
        assertTrue(attempt2 is StreamRetryDecision.Retry)
        assertEquals(1200L, (attempt2 as StreamRetryDecision.Retry).delayMs)

        val attempt3 = StreamRetryPolicy.decide(error, attempt = 3)
        assertTrue(attempt3 is StreamRetryDecision.GiveUp)
    }

    @Test
    fun test401NeverRetries() {
        val error = AppError.Unauthorized("Token expired")
        val decision = StreamRetryPolicy.decide(error, attempt = 1)
        assertTrue(decision is StreamRetryDecision.GiveUp)
    }

    @Test
    fun test429WithRetryAfter2RetriesWith30GivesUp() {
        val error429 = AppError.Server("TOO_MANY_REQUESTS", "Rate limited")

        val retryDecision = StreamRetryPolicy.decide(error429, attempt = 1, retryAfterSeconds = 2L)
        assertTrue(retryDecision is StreamRetryDecision.Retry)
        assertEquals(2000L, (retryDecision as StreamRetryDecision.Retry).delayMs)

        val giveUpDecision = StreamRetryPolicy.decide(error429, attempt = 1, retryAfterSeconds = 30L)
        assertTrue(giveUpDecision is StreamRetryDecision.GiveUp)
    }

    @Test
    fun testNewPipeNullResultRetriesOnceAfter800ms() {
        val error = AppError.Server(StreamRetryPolicy.CODE_YOUTUBE_EXTRACTION_FAILED, "Extraction failed")

        val attempt1 = StreamRetryPolicy.decide(error, attempt = 1)
        assertTrue(attempt1 is StreamRetryDecision.Retry)
        assertEquals(800L, (attempt1 as StreamRetryDecision.Retry).delayMs)

        val attempt2 = StreamRetryPolicy.decide(error, attempt = 2)
        assertTrue(attempt2 is StreamRetryDecision.GiveUp)
    }

    @Test
    fun testUserFriendlyMessages() {
        assertEquals(
            "No connection. Check your internet",
            StreamRetryPolicy.userFriendlyMessage(AppError.Network())
        )
        assertEquals(
            "Server busy, try again in a moment",
            StreamRetryPolicy.userFriendlyMessage(AppError.Server("TOO_MANY_REQUESTS", "Slow down"))
        )
        assertEquals(
            "This song can't be played right now",
            StreamRetryPolicy.userFriendlyMessage(AppError.NotFound())
        )
        assertEquals(
            "This song can't be played right now",
            StreamRetryPolicy.userFriendlyMessage(AppError.Server("STREAM_UNAVAILABLE", "No stream"))
        )
        assertEquals(
            "YouTube blocked this stream on your network",
            StreamRetryPolicy.userFriendlyMessage(AppError.Server(StreamRetryPolicy.CODE_YOUTUBE_EXTRACTION_FAILED, "NewPipe err"))
        )
    }
}

