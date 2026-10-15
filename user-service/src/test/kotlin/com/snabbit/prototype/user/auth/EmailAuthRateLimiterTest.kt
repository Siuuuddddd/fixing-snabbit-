package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.exception.AuthRateLimited
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class EmailAuthRateLimiterTest {
    @Test
    fun `email and IP keys are hashed and delay grows without revealing the dimension`() {
        val redis = mockk<StringRedisTemplate>(relaxed = true)
        val values = mockk<ValueOperations<String, String>>()
        val keys = mutableListOf<String>()
        val key = slot<String>()
        every { redis.opsForValue() } returns values
        every { values.increment(capture(key)) } answers {
            keys += key.captured
            if (key.captured.contains(":identity:")) 3 else 1
        }
        val limiter = EmailAuthRateLimiter(
            redis,
            EmailAuthProperties(
                codeHmacSecret = OpaqueToken.generate(), rateLimitPerIdentity = 2, rateLimitPerIp = 2,
                maximumDelay = Duration.ofSeconds(30),
            ),
        )
        val email = "customer@example.test"
        val ip = "192.0.2.20"

        val error = org.junit.jupiter.api.assertThrows<AuthRateLimited> { limiter.check("login", email, ip) }
        assertTrue(error.retryAfterSeconds >= 1)
        assertTrue(keys.size == 2)
        assertFalse(keys.any { it.contains(email) || it.contains(ip) })
    }
}
