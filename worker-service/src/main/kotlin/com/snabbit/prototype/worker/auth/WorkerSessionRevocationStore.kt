package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.RevokedSessionKeys
import java.time.Duration
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

interface WorkerSessionRevocationStore {
    fun revoke(sessionId: UUID, ttl: Duration)
}

@Component
class RedisWorkerSessionRevocationStore(private val redis: StringRedisTemplate) : WorkerSessionRevocationStore {
    override fun revoke(sessionId: UUID, ttl: Duration) {
        if (!ttl.isNegative && !ttl.isZero) runCatching {
            redis.opsForValue().set(RevokedSessionKeys.key(sessionId), "1", ttl)
        }.onFailure { logger.warn("Immediate session revocation cache unavailable; access token expiry remains enforced", it) }
    }

    companion object { private val logger = LoggerFactory.getLogger(RedisWorkerSessionRevocationStore::class.java) }
}
