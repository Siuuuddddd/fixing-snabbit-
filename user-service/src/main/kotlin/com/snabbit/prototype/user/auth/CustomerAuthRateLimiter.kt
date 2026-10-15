package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.exception.AuthRateLimited
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

interface CustomerAuthRateLimiter {
    fun check(scope: String, accountKey: String)
}

@Component
class RedisCustomerAuthRateLimiter(
    private val redis: StringRedisTemplate,
    private val properties: AuthProperties,
) : CustomerAuthRateLimiter {
    override fun check(scope: String, accountKey: String) {
        val key = "auth:customer:rate:$scope:${OpaqueToken.hash(accountKey)}"
        val count = redis.opsForValue().increment(key) ?: throw AuthRateLimited()
        if (count == 1L) redis.expire(key, properties.accountRateWindow)
        if (count > properties.accountRateLimit) throw AuthRateLimited()
    }
}
