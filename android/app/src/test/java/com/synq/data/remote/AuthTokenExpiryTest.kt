package com.synq.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthTokenExpiryTest {

    private fun createJwtWithExp(expSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"userId":"123","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.fakesignature"
    }

    @Test
    fun testIsAccessTokenExpiringTable() {
        val nowMs = 1_000_000_000_000L // arbitrary fixed timestamp
        val nowSec = nowMs / 1000L

        // 1. Expired: exp is 60 seconds in the past
        val expiredToken = createJwtWithExp(nowSec - 60L)
        assertTrue(
            "Expired token must return true",
            JwtUtils.isExpiredOrNearExpiry(expiredToken, marginSeconds = 30L, currentTimeSeconds = nowSec)
        )

        // 2. Within skew: exp is 15 seconds in the future (<= 30s)
        val withinSkewToken = createJwtWithExp(nowSec + 15L)
        assertTrue(
            "Token within skew window must return true",
            JwtUtils.isExpiredOrNearExpiry(withinSkewToken, marginSeconds = 30L, currentTimeSeconds = nowSec)
        )

        // 3. Valid: exp is 10 minutes in the future (> 30s)
        val validToken = createJwtWithExp(nowSec + 600L)
        assertFalse(
            "Valid token beyond skew must return false",
            JwtUtils.isExpiredOrNearExpiry(validToken, marginSeconds = 30L, currentTimeSeconds = nowSec)
        )

        // 4. Malformed tokens: must return true for isExpiredOrNearExpiry
        assertTrue(
            "Malformed token without dots must return true",
            JwtUtils.isExpiredOrNearExpiry("not-a-jwt", marginSeconds = 30L, currentTimeSeconds = nowSec)
        )
        assertTrue(
            "Malformed token with invalid base64 must return true",
            JwtUtils.isExpiredOrNearExpiry("header.invalid_base64!!!.sig", marginSeconds = 30L, currentTimeSeconds = nowSec)
        )
        assertTrue(
            "Malformed token without exp claim must return true",
            JwtUtils.isExpiredOrNearExpiry(
                "${Base64.getUrlEncoder().encodeToString("{}".toByteArray())}.${Base64.getUrlEncoder().encodeToString("""{"userId":"123"}""".toByteArray())}.sig",
                marginSeconds = 30L,
                currentTimeSeconds = nowSec
            )
        )
    }
}

