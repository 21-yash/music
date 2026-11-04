package com.synq.features.music.jam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamRecommendationPolicyTest {

    @Test
    fun `host can always dismiss recommendations regardless of perms`() {
        val noPerms = JamPermissions(queueAdd = false, playback = false, queueManage = false)
        assertTrue(canDismissRecommendation(isHost = true, perms = noPerms))

        val allPerms = JamPermissions(queueAdd = true, playback = true, queueManage = true)
        assertTrue(canDismissRecommendation(isHost = true, perms = allPerms))
    }

    @Test
    fun `guest with queueAdd only can dismiss recommendations`() {
        val perms = JamPermissions(queueAdd = true, playback = false, queueManage = false)
        assertTrue(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun `guest with queueManage only can dismiss recommendations`() {
        val perms = JamPermissions(queueAdd = false, playback = false, queueManage = true)
        assertTrue(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun `guest with both queueAdd and queueManage can dismiss recommendations`() {
        val perms = JamPermissions(queueAdd = true, playback = false, queueManage = true)
        assertTrue(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun `guest with playback only cannot dismiss recommendations`() {
        val perms = JamPermissions(queueAdd = false, playback = true, queueManage = false)
        assertFalse(canDismissRecommendation(isHost = false, perms = perms))
    }

    @Test
    fun `guest with neither queueAdd nor queueManage cannot dismiss recommendations`() {
        val perms = JamPermissions(queueAdd = false, playback = false, queueManage = false)
        assertFalse(canDismissRecommendation(isHost = false, perms = perms))
    }
}
