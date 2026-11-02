package com.synq.data.remote

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class JwtExpiryTest {

    private fun createToken(expSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"12345","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.signature"
    }

    @Test
    fun testExpiredToken() {
        val nowMs = 1_000_000_000L
        val nowSec = nowMs / 1000L
        val expiredToken = createToken(nowSec - 10)

        assertTrue(JwtUtils.isExpiredOrNearExpiry(expiredToken, marginSeconds = 30, currentTimeSeconds = nowSec))
        assertTrue(isAccessTokenExpiring(expiredToken, nowMs, skewMs = 30_000L))
    }

    @Test
    fun testExpiringSoonTokenWithinSkew() {
        val nowMs = 1_000_000_000L
        val nowSec = nowMs / 1000L
        val expiringSoonToken = createToken(nowSec + 15) // expires in 15 seconds, skew is 30s

        assertTrue(JwtUtils.isExpiredOrNearExpiry(expiringSoonToken, marginSeconds = 30, currentTimeSeconds = nowSec))
        assertTrue(isAccessTokenExpiring(expiringSoonToken, nowMs, skewMs = 30_000L))
    }

    @Test
    fun testFreshValidToken() {
        val nowMs = 1_000_000_000L
        val nowSec = nowMs / 1000L
        val freshToken = createToken(nowSec + 3600) // expires in 1 hour

        assertFalse(JwtUtils.isExpiredOrNearExpiry(freshToken, marginSeconds = 30, currentTimeSeconds = nowSec))
        assertFalse(isAccessTokenExpiring(freshToken, nowMs, skewMs = 30_000L))
    }

    @Test
    fun testMalformedOrEmptyToken() {
        val nowMs = 1_000_000_000L
        val nowSec = nowMs / 1000L

        assertTrue(JwtUtils.isExpiredOrNearExpiry(null, marginSeconds = 30, currentTimeSeconds = nowSec))
        assertFalse(isAccessTokenExpiring(null, nowMs, skewMs = 30_000L))

        assertTrue(JwtUtils.isExpiredOrNearExpiry("invalid", marginSeconds = 30, currentTimeSeconds = nowSec))
        assertFalse(isAccessTokenExpiring("invalid", nowMs, skewMs = 30_000L))
    }
}
