package com.synq.features.music.playback

class ReResolveGuard {
    var attemptedFor: String? = null
        private set

    /**
     * Marks and returns true if this songId has not been re-resolved yet.
     * Subsequent calls for the same songId return false until reset.
     */
    fun shouldReResolve(songId: String): Boolean {
        if (attemptedFor != songId) {
            attemptedFor = songId
            return true
        }
        return false
    }

    /**
     * Called when a new song starts loading or playing.
     */
    fun onSongStarted(songId: String) {
        if (attemptedFor != songId) {
            attemptedFor = null
        }
    }

    /**
     * Resets the guard when playback reaches READY state.
     */
    fun onReady() {
        attemptedFor = null
    }
}
