package com.synq.features.music.playback

import com.synq.core.domain.AppError

sealed class StreamRetryDecision {
    object GiveUp : StreamRetryDecision()
    data class Retry(val delayMs: Long) : StreamRetryDecision()
}

object StreamRetryPolicy {
    const val CODE_YOUTUBE_EXTRACTION_FAILED = "YOUTUBE_EXTRACTION_FAILED"

    fun decide(
        error: AppError,
        attempt: Int,
        retryAfterSeconds: Long? = null
    ): StreamRetryDecision {
        if (attempt > 2) return StreamRetryDecision.GiveUp

        return when (error) {
            is AppError.Unauthorized,
            is AppError.NotFound,
            is AppError.Validation -> StreamRetryDecision.GiveUp

            is AppError.Network -> {
                when (attempt) {
                    1 -> StreamRetryDecision.Retry(400L)
                    2 -> StreamRetryDecision.Retry(1200L)
                    else -> StreamRetryDecision.GiveUp
                }
            }

            is AppError.Server -> {
                val is429 = error.code == "TOO_MANY_REQUESTS" || error.code == "HTTP_429"
                val is5xx = error.code.startsWith("HTTP_5") ||
                        error.code == "INTERNAL_ERROR" ||
                        error.code == "500" ||
                        error.code == "502" ||
                        error.code == "503" ||
                        error.code == "504"

                if (is429) {
                    val delaySec = retryAfterSeconds ?: parseRetryAfter(error.message)
                    if (delaySec != null && delaySec > 3L) {
                        StreamRetryDecision.GiveUp
                    } else {
                        val waitMs = (delaySec ?: 1L) * 1000L
                        if (attempt <= 2) StreamRetryDecision.Retry(waitMs) else StreamRetryDecision.GiveUp
                    }
                } else if (is5xx) {
                    when (attempt) {
                        1 -> StreamRetryDecision.Retry(400L)
                        2 -> StreamRetryDecision.Retry(1200L)
                        else -> StreamRetryDecision.GiveUp
                    }
                } else if (error.code == CODE_YOUTUBE_EXTRACTION_FAILED) {
                    if (attempt == 1) {
                        StreamRetryDecision.Retry(800L)
                    } else {
                        StreamRetryDecision.GiveUp
                    }
                } else {
                    StreamRetryDecision.GiveUp
                }
            }

            is AppError.Unknown -> {
                when (attempt) {
                    1 -> StreamRetryDecision.Retry(400L)
                    2 -> StreamRetryDecision.Retry(1200L)
                    else -> StreamRetryDecision.GiveUp
                }
            }
        }
    }

    private fun parseRetryAfter(message: String): Long? {
        val match = Regex("""(?:retry in|after)\s*(\d+)""", RegexOption.IGNORE_CASE).find(message)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    fun userFriendlyMessage(error: AppError): String {
        return when (error) {
            is AppError.Network -> "No connection. Check your internet"
            is AppError.NotFound -> "This song can't be played right now"
            is AppError.Server -> {
                when {
                    error.code == "TOO_MANY_REQUESTS" || error.code == "HTTP_429" ->
                        "Server busy, try again in a moment"
                    error.code == "STREAM_UNAVAILABLE" ->
                        "This song can't be played right now"
                    error.code == CODE_YOUTUBE_EXTRACTION_FAILED ->
                        "YouTube blocked this stream on your network"
                    else ->
                        "This song can't be played right now"
                }
            }
            else -> "This song can't be played right now"
        }
    }
}

