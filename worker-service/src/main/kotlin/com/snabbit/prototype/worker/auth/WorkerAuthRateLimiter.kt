package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.worker.exception.WorkerAuthRateLimited
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

interface WorkerAuthRateLimiter {
    fun check(scope: String, accountKey: String)
}

@Component
class RedisWorkerAuthRateLimiter(
    private val redis: StringRedisTemplate,
    private val properties: WorkerAuthProperties,
) : WorkerAuthRateLimiter {
    override fun check(scope: String, accountKey: String) {
        val key = "auth:worker:rate:$scope:${OpaqueToken.hash(accountKey)}"
        val count = redis.opsForValue().increment(key) ?: throw WorkerAuthRateLimited()
        if (count == 1L) redis.expire(key, properties.accountRateWindow)
        if (count > properties.accountRateLimit) throw WorkerAuthRateLimited()
    }
}
