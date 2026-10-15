package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.exception.AuthRateLimited
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RedisCustomerAuthRateLimiterTest {
    private val redis = mockk<StringRedisTemplate>(relaxed = true)
    private val values = mockk<ValueOperations<String, String>>()
    private val properties = AuthProperties(
        issuer = "https://auth.snabbit.test", jwtSecret = "rate-limit-secret-that-is-at-least-32-bytes",
        otpPepper = "pepper", otpTtl = Duration.ofMinutes(5), requestCooldown = Duration.ofMinutes(1),
        accessTokenTtl = Duration.ofMinutes(15), maxAttempts = 5, exposeCode = false,
        accountRateLimit = 1,
    )

    @Test
    fun `account identifiers are hashed and excess requests are generic`() {
        val key = slot<String>()
        every { redis.opsForValue() } returns values
        every { values.increment(capture(key)) } returns 1 andThen 2
        val limiter = RedisCustomerAuthRateLimiter(redis, properties)
        val account = "verified-google-subject"

        limiter.check("sign-in", account)
        assertFalse(key.captured.contains(account))
        assertThrows(AuthRateLimited::class.java) { limiter.check("sign-in", account) }
    }
}
