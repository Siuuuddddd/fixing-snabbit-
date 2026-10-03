package com.snabbit.prototype.user.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

interface OtpChallengeStore {
    fun acquireRequestCooldown(key: String, ttl: Duration): Boolean
    fun save(key: String, codeHash: String, ttl: Duration)
    fun codeHash(key: String): String?
    fun incrementAttempts(key: String, ttl: Duration): Long
    fun clear(key: String)
}
