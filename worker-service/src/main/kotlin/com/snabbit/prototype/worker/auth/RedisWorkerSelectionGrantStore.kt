package com.snabbit.prototype.worker.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisWorkerSelectionGrantStore(
    private val redis: StringRedisTemplate,
    private val properties: WorkerAuthProperties,
) : WorkerSelectionGrantStore {
    private val random = SecureRandom()

    override fun issue(googleSubject: String, eligibleWorkerIds: Collection<UUID>): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val value = buildString {
            append(hash(googleSubject))
            eligibleWorkerIds.forEach { append('|').append(it) }
        }
        redis.opsForValue().set(key(token), value, properties.sharedSelectionTokenTtl)
        return token
    }

    override fun consume(token: String, googleSubject: String): Set<UUID>? {
        val parts = redis.opsForValue().getAndDelete(key(token))?.split('|') ?: return null
        if (parts.firstOrNull() != hash(googleSubject)) return null
        return parts.drop(1).mapTo(linkedSetOf(), UUID::fromString)
    }

    private fun key(token: String) = "auth:worker-selection:${hash(token)}"

    private fun hash(value: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
    )
}
