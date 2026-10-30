package com.synq.features.music.playback

class AutoSkipPolicy(
    val maxConsecutiveSkips: Int = 3
) {
    var consecutiveFailures: Int = 0
        private set

    /**
     * Called when an unrecoverable playback failure occurs.
     * Returns true for failures 1..maxConsecutiveSkips (should skip),
     * and false on the (maxConsecutiveSkips + 1)-th failure (giving up and resetting).
     */
    fun onFailure(): Boolean {
        consecutiveFailures++
        return if (consecutiveFailures <= maxConsecutiveSkips) {
            true
        } else {
            consecutiveFailures = 0
            false
        }
    }

    /**
     * Resets failure counter when playback becomes ready.
     */
    fun onReady() {
        consecutiveFailures = 0
    }

    /**
     * Checks if there is a next track in the queue to skip forward to,
     * preventing infinite loops when at the end of the queue.
     */
    fun canSkipForward(currentIndex: Int, queueSize: Int): Boolean {
        return currentIndex + 1 < queueSize
    }
}

