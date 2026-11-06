package com.synq.features.music.jam

sealed class HealthCheckAction {
    object DoNothing : HealthCheckAction()
    data class Retry(val delayMs: Long, val timeoutMs: Long) : HealthCheckAction()
    object DisconnectAndReconnect : HealthCheckAction()
}

object HealthCheckPolicy {
    const val PING_TIMEOUT_MS = 5000L
    const val RETRY_DELAY_MS = 1000L

    fun onFirstPingResult(success: Boolean): HealthCheckAction {
        return if (success) {
            HealthCheckAction.DoNothing
        } else {
            HealthCheckAction.Retry(RETRY_DELAY_MS, PING_TIMEOUT_MS)
        }
    }

    fun onRetryPingResult(success: Boolean): HealthCheckAction {
        return if (success) {
            HealthCheckAction.DoNothing
        } else {
            HealthCheckAction.DisconnectAndReconnect
        }
    }
}

