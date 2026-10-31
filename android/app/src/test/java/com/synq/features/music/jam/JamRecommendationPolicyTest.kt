package com.synq.features.music.jam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamRecommendationPolicyTest {

    @Test
    fun testCanDismissRecommendation_hostTrue_anyPerms_returnsTrue() {
        val noPerms = JamPermissions(queueAdd = false, playback = false, queueManage = false)
        assertTrue(canDismissRecommendation(isHost = true, perms = noPerms))
    }

    @Test
    fun testCanDismissRecommendation_hostFalse_queueAddTrue_returnsTrue() {
        val perms = JamPermissions(queueAdd = true, playback = false, queueManage = false)
        assertTrue(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun testCanDismissRecommendation_hostFalse_queueManageTrue_returnsTrue() {
        val perms = JamPermissions(queueAdd = false, playback = false, queueManage = true)
        assertTrue(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun testCanDismissRecommendation_hostFalse_onlyPlaybackTrue_returnsFalse() {
        val perms = JamPermissions(queueAdd = false, playback = true, queueManage = false)
        assertFalse(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun testCanDismissRecommendation_hostFalse_noPerms_returnsFalse() {
        val perms = JamPermissions(queueAdd = false, playback = false, queueManage = false)
        assertFalse(canDismissRecommendation(isHost = false, perms = perms))
    }
}
