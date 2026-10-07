package com.snabbit.prototype.user.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RegistrationGrantStore(private val redis: StringRedisTemplate) {
    private val random = SecureRandom()

    fun issue(phone: String, ttl: Duration): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val grant = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        redis.opsForValue().set("auth:registration:${hash(grant)}", phone, ttl)
        return grant
    }

    fun consume(grant: String): String? {
        val key = "auth:registration:${hash(grant)}"
        return redis.opsForValue().getAndDelete(key)
    }

    private fun hash(value: String) = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()),
    )
}
