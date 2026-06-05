package com.mangalens

import com.mangalens.core.common.FrameRateLimiter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateLimiterTest {
    @Test
    fun shouldRun_allows_after_interval() {
        val limiter = FrameRateLimiter(minIntervalMs = 500)

        assertTrue(limiter.shouldRun(1_000))
        assertFalse(limiter.shouldRun(1_100))
        assertTrue(limiter.shouldRun(1_600))
    }
}
