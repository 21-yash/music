package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamResolutionPlanTest {

    @Test
    fun testChoose_youtubeProvider_returnsDirectYouTube() {
        val plan = StreamResolutionPlan.choose("youtube", "dQw4w9WgXcQ")
        assertEquals(StreamResolutionPlan.Plan.DIRECT_YOUTUBE, plan)
    }

    @Test
    fun testChoose_jiosaavnProvider_returnsBackendThenFallback() {
        val plan = StreamResolutionPlan.choose("jiosaavn", "g6Ky7cGO")
        assertEquals(StreamResolutionPlan.Plan.BACKEND_THEN_FALLBACK, plan)
    }

    @Test
    fun testChoose_unknownProvider_11CharYoutubeId_returnsDirectYouTube() {
        val plan = StreamResolutionPlan.choose("unknown", "dQw4w9WgXcQ")
        assertEquals(StreamResolutionPlan.Plan.DIRECT_YOUTUBE, plan)
    }

    @Test
    fun testChoose_spotifyProvider_returnsBackendThenFallback() {
        val plan = StreamResolutionPlan.choose("spotify", "4cOdK2wGLETKBW3PvgPWqT")
        assertEquals(StreamResolutionPlan.Plan.BACKEND_THEN_FALLBACK, plan)
    }

    @Test
    fun testChoose_nullProvider_non11Char_returnsBackendThenFallback() {
        val plan = StreamResolutionPlan.choose(null, "short")
        assertEquals(StreamResolutionPlan.Plan.BACKEND_THEN_FALLBACK, plan)
    }
}
