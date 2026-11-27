package com.synq.features.sports.notifications

import com.synq.data.remote.dto.SportsNotificationPrefsDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveMatchTrackerTest {

    @Test
    fun `tracks currently viewed match and suppresses duplicate pushes for active match`() {
        val tracker = ActiveMatchTracker()
        assertFalse(tracker.isViewingMatch("10293"))

        tracker.enterMatch("10293")
        assertTrue(tracker.isViewingMatch("10293"))
        assertFalse(tracker.isViewingMatch("99999"))

        tracker.exitMatch("10293")
        assertFalse(tracker.isViewingMatch("10293"))
    }

    @Test
    fun `popping second match restores previous match suppression on backstack`() {
        val tracker = ActiveMatchTracker()
        tracker.enterMatch("151352")
        tracker.enterMatch("161296")

        assertTrue(tracker.isViewingMatch("161296"))
        assertFalse(tracker.isViewingMatch("151352"))

        // Navigating back from 161296 -> 151352 keeps 151352 active
        tracker.exitMatch("161296")
        assertTrue(tracker.isViewingMatch("151352"))
        assertFalse(tracker.isViewingMatch("161296"))

        tracker.exitMatch("151352")
        assertFalse(tracker.isViewingMatch("151352"))
    }

    @Test
    fun `default sports notification preferences enable all 5 alert types`() {
        val defaults = SportsNotificationPrefsDto()
        assertTrue(defaults.enabled)
        assertTrue(defaults.matchStartingSoon)
        assertTrue(defaults.matchStarted)
        assertTrue(defaults.matchConcluded)
        assertTrue(defaults.milestones)
        assertTrue(defaults.matchUpdates)
    }
}
