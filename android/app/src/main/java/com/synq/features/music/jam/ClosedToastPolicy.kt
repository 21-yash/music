package com.synq.features.music.jam

/**
 * Task H2: Pure decision function for toasts on Jam closure.
 * Ensures exactly one toast for kick, ended, or expired sessions,
 * and zero toasts on voluntary leave.
 */
fun closedToastFor(reason: String, alreadyToasted: Boolean = false): String? {
    if (alreadyToasted) return null
    return when (reason) {
        "kicked" -> null // Handled exclusively by JamUiEvent.Kicked ("You were removed from the Jam by the host")
        "ended" -> "The host ended this Jam"
        "expired" -> "This Jam has expired"
        "connection_lost" -> "Connection lost. Please rejoin the Jam."
        else -> null
    }
}

