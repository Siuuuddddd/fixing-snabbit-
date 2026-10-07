package com.snabbit.prototype.worker.auth

import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisWorkerOtpStore(private val redis: StringRedisTemplate) : WorkerOtpStore {
    override fun acquireCooldown(key: String, ttl: Duration): Boolean =
        redis.opsForValue().setIfAbsent("auth:worker:cooldown:$key", "1", ttl) == true

    override fun releaseCooldown(key: String) { redis.delete("auth:worker:cooldown:$key") }

    override fun incrementAttempts(key: String, ttl: Duration): Long {
        val attemptsKey = "auth:worker:attempts:$key"
        val attempts = redis.opsForValue().increment(attemptsKey) ?: 0
        redis.expire(attemptsKey, ttl)
        return attempts
    }

    override fun clear(key: String) {
        redis.delete("auth:worker:attempts:$key")
    }
}
