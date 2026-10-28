package com.synq.data.remote

import java.nio.charset.StandardCharsets

object JwtUtils {

    /**
     * Checks if a JWT token is missing, unparsable, expired, or near expiry (within marginSeconds).
     */
    fun isExpiredOrNearExpiry(
        token: String?,
        marginSeconds: Long = 30L,
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000
    ): Boolean {
        val exp = getExpirationSeconds(token) ?: return true
        return currentTimeSeconds + marginSeconds >= exp
    }

    /**
     * Extracts the 'exp' claim (in seconds) from a JWT token's payload without external libraries.
     * Tolerates Base64 URL-safe encoding and missing padding.
     */
    fun getExpirationSeconds(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null

        return try {
            var payload = parts[1].replace('-', '+').replace('_', '/')
            val pad = payload.length % 4
            if (pad > 0) {
                payload += "=".repeat(4 - pad)
            }
            val bytes = java.util.Base64.getDecoder().decode(payload)
            val json = String(bytes, StandardCharsets.UTF_8)

            // Extract "exp": <digits>
            val regex = """"exp"\s*:\s*(\d+)""".toRegex()
            regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
