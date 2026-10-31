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
        val skewMs = 30_000L // 30 seconds skew

        // 1. Expired: exp is 60 seconds in the past
        val expiredToken = createJwtWithExp(nowSec - 60L)
        assertTrue(
            "Expired token must return true",
            isAccessTokenExpiring(expiredToken, nowMs, skewMs)
        )

        // 2. Within skew: exp is 15 seconds in the future (<= 30s)
        val withinSkewToken = createJwtWithExp(nowSec + 15L)
        assertTrue(
            "Token within skew window must return true",
            isAccessTokenExpiring(withinSkewToken, nowMs, skewMs)
        )

        // 3. Valid: exp is 10 minutes in the future (> 30s)
        val validToken = createJwtWithExp(nowSec + 600L)
        assertFalse(
            "Valid token beyond skew must return false",
            isAccessTokenExpiring(validToken, nowMs, skewMs)
        )

        // 4. Malformed tokens: must return false (so caller behaves as today)
        assertFalse(
            "Malformed token without dots must return false",
            isAccessTokenExpiring("not-a-jwt", nowMs, skewMs)
        )
        assertFalse(
            "Malformed token with invalid base64 must return false",
            isAccessTokenExpiring("header.invalid_base64!!!.sig", nowMs, skewMs)
        )
        assertFalse(
            "Malformed token without exp claim must return false",
            isAccessTokenExpiring(
                "${Base64.getUrlEncoder().encodeToString("{}".toByteArray())}.${Base64.getUrlEncoder().encodeToString("""{"userId":"123"}""".toByteArray())}.sig",
                nowMs,
                skewMs
            )
        )
    }
}

