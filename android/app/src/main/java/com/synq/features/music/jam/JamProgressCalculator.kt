package com.synq.features.music.jam

data class JamProgress(
    val durationMs: Long,
    val fraction: Float,
    val canSeek: Boolean
)

fun computeJamProgress(
    metadataDurationSec: Int?,
    playerDurationMs: Long,
    positionMs: Long
): JamProgress {
    val durationMs = if (metadataDurationSec != null && metadataDurationSec > 0) {
        metadataDurationSec * 1000L
    } else if (playerDurationMs > 0L) {
        playerDurationMs
    } else {
        0L
    }

    return if (durationMs > 0L) {
        val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        JamProgress(
            durationMs = durationMs,
            fraction = fraction,
            canSeek = true
        )
    } else {
        JamProgress(
            durationMs = 0L,
            fraction = 0f,
            canSeek = false
        )
    }
}
