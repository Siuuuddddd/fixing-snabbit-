package com.snabbit.prototype.worker.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisWorkerOtpStore(private val redis: StringRedisTemplate) : WorkerOtpStore {
    override fun acquireCooldown(key: String, ttl: Duration): Boolean =
        redis.opsForValue().setIfAbsent("auth:worker:cooldown:$key", "1", ttl) == true

    override fun save(key: String, hash: String, ttl: Duration) {
        redis.opsForValue().set("auth:worker:code:$key", hash, ttl)
        redis.delete("auth:worker:attempts:$key")
    }

    override fun hash(key: String): String? = redis.opsForValue().get("auth:worker:code:$key")

    override fun incrementAttempts(key: String, ttl: Duration): Long {
        val attemptsKey = "auth:worker:attempts:$key"
        val attempts = redis.opsForValue().increment(attemptsKey) ?: 0
        redis.expire(attemptsKey, ttl)
        return attempts
    }

    override fun clear(key: String) {
        redis.delete(listOf("auth:worker:code:$key", "auth:worker:attempts:$key"))
    }
}
