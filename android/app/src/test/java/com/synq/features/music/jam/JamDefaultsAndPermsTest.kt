package com.synq.features.music.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamDefaultsAndPermsTest {

    @Test
    fun testBuildSetDefaultsPayload_withApplyToExistingTrue_includesFlagAndPerms() {
        val perms = JamPermissions(playback = true, queueAdd = false, queueManage = true)
        val payload = buildSetDefaultsPayload(perms, applyToExisting = true)

        val innerPerms = payload.getJSONObject("perms")
        assertTrue(innerPerms.getBoolean("playback"))
        assertFalse(innerPerms.getBoolean("queueAdd"))
        assertTrue(innerPerms.getBoolean("queueManage"))

        assertTrue(payload.has("applyToExisting"))
        assertTrue(payload.getBoolean("applyToExisting"))
    }

    @Test
    fun testBuildSetDefaultsPayload_withApplyToExistingFalse_omitsFlag() {
        val perms = JamPermissions(playback = false, queueAdd = true, queueManage = false)
        val payload = buildSetDefaultsPayload(perms, applyToExisting = false)

        val innerPerms = payload.getJSONObject("perms")
        assertFalse(innerPerms.getBoolean("playback"))
        assertTrue(innerPerms.getBoolean("queueAdd"))
        assertFalse(innerPerms.getBoolean("queueManage"))

        assertFalse(payload.has("applyToExisting"))
    }

    @Test
    fun testJamStateStore_applySnapshotWithPlaybackFalseThenTrue_flipsCanControlPlayback() {
        val myUserId = "guest_1"
        val store = JamStateStore(myUserId = myUserId)

        val memberFalse = JamMember(
            userId = myUserId,
            username = "Guest 1",
            perms = JamPermissions(playback = false, queueAdd = true, queueManage = false)
        )
        val snapshot1 = RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 1000L,
            hostUserId = "host_1",
            createdAt = 1000L,
            members = listOf(
                JamMember(userId = "host_1", username = "Host", perms = JamPermissions(playback = true, queueAdd = true, queueManage = true)),
                memberFalse
            ),
            defaults = JamPermissions(playback = false, queueAdd = true, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying()
        )

        val applied1 = store.applySnapshot(snapshot1, isEpoch = true)
        assertTrue(applied1)
        assertFalse("Guest should not have playback permission initially", store.uiState.value.canControlPlayback)

        // Snapshot 2: host changes defaults / perms with applyToExisting -> guest now has playback=true
        val memberTrue = JamMember(
            userId = myUserId,
            username = "Guest 1",
            perms = JamPermissions(playback = true, queueAdd = true, queueManage = false)
        )
        val snapshot2 = snapshot1.copy(
            version = 2L,
            members = listOf(
                JamMember(userId = "host_1", username = "Host", perms = JamPermissions(playback = true, queueAdd = true, queueManage = true)),
                memberTrue
            ),
            defaults = JamPermissions(playback = true, queueAdd = true, queueManage = false)
        )

        val applied2 = store.applySnapshot(snapshot2, isEpoch = false)
        assertTrue(applied2)
        assertTrue("Guest uiState.canControlPlayback must flip to true on new snapshot", store.uiState.value.canControlPlayback)
    }
}

