package com.synq.data.remote

import java.io.IOException

/**
 * Thrown when an HTTP 429 (Too Many Requests) response is received.
 * Carries the parsed Retry-After duration in seconds.
 */
class RateLimitException(
    val retryAfterSeconds: Long
) : IOException("Rate limited by server. Retry in $retryAfterSeconds seconds.")
