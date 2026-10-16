package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.exception.AuthRateLimited
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class PhoneAuthRateLimiter(
    private val redis: StringRedisTemplate,
    private val properties: PhoneAuthProperties,
) {
    fun acquireSend(phoneE164: String, ipAddress: String) {
        checkIp("send", ipAddress)
        val identity = OpaqueToken.hash(phoneE164)
        val cooldownKey = "auth:phone:send-cooldown:$identity"
        if (redis.opsForValue().setIfAbsent(cooldownKey, "1", properties.resendCooldown) != true) {
            throw AuthRateLimited(ttl(cooldownKey, properties.resendCooldown.seconds))
        }
        val hourlyKey = "auth:phone:send-hour:$identity"
        val count = increment(hourlyKey, Duration.ofHours(1))
        if (count > properties.maxSendsPerHour) {
            redis.delete(cooldownKey)
            throw AuthRateLimited(ttl(hourlyKey, 60))
        }
    }

    fun releaseFailedSend(phoneE164: String) {
        val identity = OpaqueToken.hash(phoneE164)
        redis.delete("auth:phone:send-cooldown:$identity")
        val hourlyKey = "auth:phone:send-hour:$identity"
        val count = redis.opsForValue().decrement(hourlyKey)
        if (count != null && count <= 0) redis.delete(hourlyKey)
    }

    fun checkVerification(ipAddress: String) = checkIp("verify", ipAddress)

    private fun checkIp(scope: String, ipAddress: String) {
        val key = "auth:phone:rate:$scope:ip:${OpaqueToken.hash(ipAddress)}"
        if (increment(key, properties.rateLimitWindow) > properties.rateLimitPerIp) {
            throw AuthRateLimited(ttl(key, properties.rateLimitWindow.seconds))
        }
    }

    private fun increment(key: String, expiry: Duration): Long {
        val count = redis.opsForValue().increment(key) ?: throw AuthRateLimited()
        if (count == 1L) redis.expire(key, expiry)
        return count
    }

    private fun ttl(key: String, fallback: Long): Long = redis.getExpire(key).takeIf { it > 0 } ?: fallback
}
