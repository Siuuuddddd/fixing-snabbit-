package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.exception.AuthRateLimited
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class EmailAuthRateLimiter(
    private val redis: StringRedisTemplate,
    private val properties: EmailAuthProperties,
) {
    fun check(scope: String, normalizedEmail: String, ipAddress: String) {
        val identityCount = increment("identity", scope, normalizedEmail)
        val ipCount = increment("ip", scope, ipAddress)
        val excess = maxOf(identityCount - properties.rateLimitPerIdentity, ipCount - properties.rateLimitPerIp)
        if (excess > 0) {
            val shift = (excess - 1).coerceAtMost(20).toInt()
            val delay = 1L.shl(shift)
            throw AuthRateLimited(delay.coerceAtMost(properties.maximumDelay.seconds))
        }
    }

    private fun increment(dimension: String, scope: String, value: String): Long {
        val key = "auth:email:rate:$scope:$dimension:${OpaqueToken.hash(value)}"
        val count = redis.opsForValue().increment(key) ?: throw AuthRateLimited()
        if (count == 1L) redis.expire(key, properties.rateLimitWindow)
        return count
    }
}
