package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmRangeTest {

    @Test
    fun testPlan_nullTotalLength_zeroCached_warmsToDefaultTarget() {
        val plan = WarmRange.plan(totalLengthKnown = null, cachedBytes = 0L)
        assertTrue(plan is WarmRange.RangePlan.Warm)
        val warm = plan as WarmRange.RangePlan.Warm
        assertEquals(0L, warm.startByte)
        assertEquals(524_287L, warm.endByte)
    }

    @Test
    fun testPlan_shortTrackKnown_warmsToTrackLength() {
        val plan = WarmRange.plan(totalLengthKnown = 200_000L, cachedBytes = 0L)
        assertTrue(plan is WarmRange.RangePlan.Warm)
        val warm = plan as WarmRange.RangePlan.Warm
        assertEquals(0L, warm.startByte)
        assertEquals(199_999L, warm.endByte)
    }

    @Test
    fun testPlan_alreadyWarmed_returnsAlreadyWarmed() {
        val plan = WarmRange.plan(totalLengthKnown = null, cachedBytes = 524_288L)
        assertEquals(WarmRange.RangePlan.AlreadyWarmed, plan)
    }

    @Test
    fun testPlan_shortTrackAlreadyCached_returnsAlreadyWarmed() {
        val plan = WarmRange.plan(totalLengthKnown = 200_000L, cachedBytes = 200_000L)
        assertEquals(WarmRange.RangePlan.AlreadyWarmed, plan)
    }

    @Test
    fun testPlan_partiallyCached_warmsRemainingToTarget() {
        val plan = WarmRange.plan(totalLengthKnown = null, cachedBytes = 100_000L)
        assertTrue(plan is WarmRange.RangePlan.Warm)
        val warm = plan as WarmRange.RangePlan.Warm
        assertEquals(100_000L, warm.startByte)
        assertEquals(524_287L, warm.endByte)
    }
}
