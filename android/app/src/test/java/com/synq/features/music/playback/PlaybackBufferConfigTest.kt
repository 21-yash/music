package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBufferConfigTest {

    @Test
    fun testBufferDurationsMatchSpecification() {
        assertEquals(15_000, PlaybackBufferConfig.MIN_BUFFER_MS)
        assertEquals(50_000, PlaybackBufferConfig.MAX_BUFFER_MS)
        assertEquals(1_000, PlaybackBufferConfig.BUFFER_FOR_PLAYBACK_MS)
        assertEquals(3_000, PlaybackBufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
    }
}
