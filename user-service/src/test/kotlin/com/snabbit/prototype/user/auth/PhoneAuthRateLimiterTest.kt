package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.exception.AuthRateLimited
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class PhoneAuthRateLimiterTest {
    private val nationalNumber = "9" + "0".repeat(8) + "1"
    private val phoneNumber = "+91$nationalNumber"

    @Test
    fun `phone and IP never appear in Redis keys`() {
        val redis = mockk<StringRedisTemplate>(relaxed = true)
        val values = mockk<ValueOperations<String, String>>()
        val keys = mutableListOf<String>()
        every { redis.opsForValue() } returns values
        every { values.increment(any()) } answers {
            keys += firstArg<String>()
            1
        }
        every { values.setIfAbsent(any(), any(), any<Duration>()) } answers {
            keys += firstArg<String>()
            true
        }

        PhoneAuthRateLimiter(redis, PhoneAuthProperties()).acquireSend(phoneNumber, "192.0.2.10")

        assertTrue(keys.isNotEmpty())
        assertFalse(keys.any { it.contains(nationalNumber) || it.contains("192.0.2.10") })
    }

    @Test
    fun `cooldown returns retry after without consuming another hourly send`() {
        val redis = mockk<StringRedisTemplate>(relaxed = true)
        val values = mockk<ValueOperations<String, String>>()
        every { redis.opsForValue() } returns values
        every { values.increment(any()) } returns 1
        every { values.setIfAbsent(any(), any(), any<Duration>()) } returns false
        every { redis.getExpire(any<String>()) } returns 17

        val error = assertThrows(AuthRateLimited::class.java) {
            PhoneAuthRateLimiter(redis, PhoneAuthProperties()).acquireSend(phoneNumber, "192.0.2.10")
        }

        assertEquals(17, error.retryAfterSeconds)
        io.mockk.verify(exactly = 1) { values.increment(match { it.contains(":ip:") }) }
        io.mockk.verify(exactly = 0) { values.increment(match { it.contains("send-hour") }) }
    }
}
