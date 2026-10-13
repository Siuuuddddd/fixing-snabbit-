package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.exception.ArrivalOtpConflict
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class ArrivalOtpVerificationRateLimiter(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) {
    fun check(challengeId: UUID, workerId: UUID, window: Duration, visitLimit: Int, workerLimit: Int) {
        val bucket = clock.instant().epochSecond / window.seconds
        increment("arrival-otp:verify:visit:$challengeId:$bucket", window, visitLimit)
        increment("arrival-otp:verify:worker:$workerId:$bucket", window, workerLimit)
    }

    private fun increment(key: String, window: Duration, limit: Int) {
        val count = redis.opsForValue().increment(key) ?: 1
        if (count == 1L) redis.expire(key, window.plusSeconds(1))
        if (count > limit) {
            throw ArrivalOtpConflict(
                "ARRIVAL_OTP_RATE_LIMITED",
                "Too many arrival OTP verification attempts. Please wait and try again.",
                window.seconds,
            )
        }
    }
}
