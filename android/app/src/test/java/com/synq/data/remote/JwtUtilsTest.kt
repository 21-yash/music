package com.synq.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtUtilsTest {

    private fun createTestJwt(expSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"userId":"u1","exp":$expSeconds}""".toByteArray())
        val signature = "dummy_signature"
        return "$header.$payload.$signature"
    }

    @Test
    fun testValidUnexpiredToken() {
        val now = 1000000L
        val exp = now + 600L // 10 minutes in future
        val token = createTestJwt(exp)

        val parsedExp = JwtUtils.getExpirationSeconds(token)
        assertEquals(exp, parsedExp)

        assertFalse(JwtUtils.isExpiredOrNearExpiry(token, marginSeconds = 30L, currentTimeSeconds = now))
    }

    @Test
    fun testNearExpiryTokenWithinMargin() {
        val now = 1000000L
        val exp = now + 20L // Only 20s remaining, less than 30s margin
        val token = createTestJwt(exp)

        assertTrue(JwtUtils.isExpiredOrNearExpiry(token, marginSeconds = 30L, currentTimeSeconds = now))
    }

    @Test
    fun testAlreadyExpiredToken() {
        val now = 1000000L
        val exp = now - 100L // Already expired
        val token = createTestJwt(exp)

        assertTrue(JwtUtils.isExpiredOrNearExpiry(token, marginSeconds = 30L, currentTimeSeconds = now))
    }

    @Test
    fun testNullOrEmptyOrCorruptedTokens() {
        val now = 1000000L
        assertTrue(JwtUtils.isExpiredOrNearExpiry(null, marginSeconds = 30L, currentTimeSeconds = now))
        assertTrue(JwtUtils.isExpiredOrNearExpiry("", marginSeconds = 30L, currentTimeSeconds = now))
        assertTrue(JwtUtils.isExpiredOrNearExpiry("not-a-jwt", marginSeconds = 30L, currentTimeSeconds = now))
        assertTrue(JwtUtils.isExpiredOrNearExpiry("a.invalid_base64_json!@#.c", marginSeconds = 30L, currentTimeSeconds = now))

        assertNull(JwtUtils.getExpirationSeconds(null))
        assertNull(JwtUtils.getExpirationSeconds(""))
        assertNull(JwtUtils.getExpirationSeconds("corrupted"))
    }
}
