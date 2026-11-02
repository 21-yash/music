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
        val nowSec = 1_000_000L
        val expiredToken = createToken(nowSec - 10)

        assertTrue(JwtUtils.isExpiredOrNearExpiry(expiredToken, marginSeconds = 30, currentTimeSeconds = nowSec))
    }

    @Test
    fun testExpiringSoonTokenWithinSkew() {
        val nowSec = 1_000_000L
        val expiringSoonToken = createToken(nowSec + 15) // expires in 15 seconds, skew is 30s

        assertTrue(JwtUtils.isExpiredOrNearExpiry(expiringSoonToken, marginSeconds = 30, currentTimeSeconds = nowSec))
    }

    @Test
    fun testFreshValidToken() {
        val nowSec = 1_000_000L
        val freshToken = createToken(nowSec + 3600) // expires in 1 hour

        assertFalse(JwtUtils.isExpiredOrNearExpiry(freshToken, marginSeconds = 30, currentTimeSeconds = nowSec))
    }

    @Test
    fun testMalformedOrEmptyToken() {
        val nowSec = 1_000_000L

        assertTrue(JwtUtils.isExpiredOrNearExpiry(null, marginSeconds = 30, currentTimeSeconds = nowSec))
        assertTrue(JwtUtils.isExpiredOrNearExpiry("", marginSeconds = 30, currentTimeSeconds = nowSec))
        assertTrue(JwtUtils.isExpiredOrNearExpiry("invalid", marginSeconds = 30, currentTimeSeconds = nowSec))
    }
}
