package com.snabbit.prototype.user.auth

import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisOtpChallengeStore(private val redis: StringRedisTemplate) : OtpChallengeStore {
    override fun acquireRequestCooldown(key: String, ttl: Duration): Boolean =
        redis.opsForValue().setIfAbsent("auth:otp:cooldown:$key", "1", ttl) == true

    override fun releaseRequestCooldown(key: String) { redis.delete("auth:otp:cooldown:$key") }

    override fun incrementAttempts(key: String, ttl: Duration): Long {
        val attemptsKey = "auth:otp:attempts:$key"
        val attempts = redis.opsForValue().increment(attemptsKey) ?: 0
        redis.expire(attemptsKey, ttl)
        return attempts
    }

    override fun clear(key: String) {
        redis.delete("auth:otp:attempts:$key")
    }
}
