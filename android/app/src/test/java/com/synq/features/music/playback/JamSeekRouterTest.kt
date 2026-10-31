package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class JamSeekRouterTest {

    @Test
    fun testTableAllCombinations() {
        data class TestCase(
            val isInJam: Boolean,
            val isExternalController: Boolean,
            val canPlayback: Boolean,
            val expected: JamSeekRouter.Route,
            val description: String
        )

        val table = listOf(
            TestCase(
                isInJam = false,
                isExternalController = false,
                canPlayback = false,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "Not in Jam, internal controller -> pass through"
            ),
            TestCase(
                isInJam = false,
                isExternalController = false,
                canPlayback = true,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "Not in Jam, internal controller -> pass through"
            ),
            TestCase(
                isInJam = false,
                isExternalController = true,
                canPlayback = false,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "Not in Jam, external controller -> pass through"
            ),
            TestCase(
                isInJam = false,
                isExternalController = true,
                canPlayback = true,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "Not in Jam, external controller -> pass through"
            ),
            TestCase(
                isInJam = true,
                isExternalController = false,
                canPlayback = false,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "In Jam, internal controller (reconciler local sync) -> pass through"
            ),
            TestCase(
                isInJam = true,
                isExternalController = false,
                canPlayback = true,
                expected = JamSeekRouter.Route.PASS_THROUGH,
                description = "In Jam, internal controller (reconciler local sync) -> pass through"
            ),
            TestCase(
                isInJam = true,
                isExternalController = true,
                canPlayback = false,
                expected = JamSeekRouter.Route.DROP,
                description = "In Jam, external controller, no playback permission -> drop"
            ),
            TestCase(
                isInJam = true,
                isExternalController = true,
                canPlayback = true,
                expected = JamSeekRouter.Route.ROUTE_TO_JAM,
                description = "In Jam, external controller, with playback permission -> route to Jam"
            )
        )

        for (tc in table) {
            val actual = JamSeekRouter.route(
                isInJam = tc.isInJam,
                isExternalController = tc.isExternalController,
                canPlayback = tc.canPlayback
            )
            assertEquals(tc.description, tc.expected, actual)
        }
    }
}
