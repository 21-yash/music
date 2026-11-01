package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSourcePolicyTest {

    @Test
    fun testChooseSource_partialNoSavedUri_isNetwork() {
        val source = PlaybackSourcePolicy.chooseSource(fullyCached = false, savedUri = null)
        assertEquals(PlaybackSourcePolicy.SourceType.NETWORK, source)
    }

    @Test
    fun testChooseSource_fullNoSavedUri_isCacheFakeUrl() {
        val source = PlaybackSourcePolicy.chooseSource(fullyCached = true, savedUri = null)
        assertEquals(PlaybackSourcePolicy.SourceType.CACHE_FAKE_URL, source)
    }

    @Test
    fun testChooseSource_fullWithSavedUri_isCacheWithSavedUri() {
        val source = PlaybackSourcePolicy.chooseSource(fullyCached = true, savedUri = "https://cdn.example.com/stream.mp4")
        assertEquals(PlaybackSourcePolicy.SourceType.CACHE_WITH_SAVED_URI, source)
    }

    @Test
    fun testOnErrorAction_fullCacheFirstTimeOnline_isEvictAndRetryNetwork() {
        val action = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = true,
            alreadyRetried = false,
            online = true
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.EVICT_AND_RETRY_NETWORK, action)
    }

    @Test
    fun testOnErrorAction_networkErrorFirstTimeOnline_isReResolveOnce() {
        val action = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = false,
            alreadyRetried = false,
            online = true
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.RE_RESOLVE_ONCE, action)
    }

    @Test
    fun testOnErrorAction_secondTime_isGiveUp() {
        val actionFromCache = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = true,
            alreadyRetried = true,
            online = true
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.GIVE_UP, actionFromCache)

        val actionFromNetwork = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = false,
            alreadyRetried = true,
            online = true
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.GIVE_UP, actionFromNetwork)
    }

    @Test
    fun testOnErrorAction_offline_isGiveUp() {
        val actionFromCache = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = true,
            alreadyRetried = false,
            online = false
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.GIVE_UP, actionFromCache)

        val actionFromNetwork = PlaybackSourcePolicy.onErrorAction(
            playingFromFullCache = false,
            alreadyRetried = false,
            online = false
        )
        assertEquals(PlaybackSourcePolicy.ErrorAction.GIVE_UP, actionFromNetwork)
    }

    @Test
    fun testDecideSavedUriReuse_freshSavedUri_notFullyCached_reuses() {
        val decision = PlaybackSourcePolicy.decideSavedUriReuse(
            isFullyCached = false,
            savedUri = "https://rr1---sn-4g5ednss.googlevideo.com/videoplayback?id=123",
            uriAgeMs = 15 * 60 * 1000L // 15 mins old
        )
        assertEquals(PlaybackSourcePolicy.SavedUriDecision.REUSE, decision)
        assertEquals(true, PlaybackSourcePolicy.shouldReuseSavedUri(false, "https://example.com/stream", 15 * 60 * 1000L))
    }

    @Test
    fun testDecideSavedUriReuse_olderThanLimit_extracts() {
        val decision = PlaybackSourcePolicy.decideSavedUriReuse(
            isFullyCached = false,
            savedUri = "https://rr1---sn-4g5ednss.googlevideo.com/videoplayback?id=123",
            uriAgeMs = 2 * 60 * 60 * 1000L + 1L // 2 hours + 1 ms old
        )
        assertEquals(PlaybackSourcePolicy.SavedUriDecision.EXTRACT, decision)
        assertEquals(false, PlaybackSourcePolicy.shouldReuseSavedUri(false, "https://example.com/stream", 3 * 60 * 60 * 1000L))
    }

    @Test
    fun testDecideSavedUriReuse_noSavedUri_extracts() {
        val decision = PlaybackSourcePolicy.decideSavedUriReuse(
            isFullyCached = false,
            savedUri = null,
            uriAgeMs = null
        )
        assertEquals(PlaybackSourcePolicy.SavedUriDecision.EXTRACT, decision)
        assertEquals(false, PlaybackSourcePolicy.shouldReuseSavedUri(false, null, null))
    }

    @Test
    fun testDecideSavedUriReuse_blankSavedUri_extracts() {
        val decision = PlaybackSourcePolicy.decideSavedUriReuse(
            isFullyCached = false,
            savedUri = "   ",
            uriAgeMs = 1000L
        )
        assertEquals(PlaybackSourcePolicy.SavedUriDecision.EXTRACT, decision)
    }

    @Test
    fun testDecideSavedUriReuse_negativeAge_extracts() {
        val decision = PlaybackSourcePolicy.decideSavedUriReuse(
            isFullyCached = false,
            savedUri = "https://example.com/stream",
            uriAgeMs = -100L
        )
        assertEquals(PlaybackSourcePolicy.SavedUriDecision.EXTRACT, decision)
    }
}

