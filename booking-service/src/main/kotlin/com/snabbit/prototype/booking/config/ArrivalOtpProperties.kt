package com.snabbit.prototype.booking.config

import java.time.Duration

data class ArrivalOtpProperties(
    val required: Boolean = true,
    val expiry: Duration = Duration.ofMinutes(5),
    val maxAttempts: Int = 5,
    val resendCooldown: Duration = Duration.ofSeconds(30),
    val maxSends: Int = 3,
) {
    init {
        require(expiry > Duration.ZERO) { "booking.arrival-otp.expiry must be positive" }
        require(maxAttempts > 0) { "booking.arrival-otp.max-attempts must be positive" }
        require(!resendCooldown.isNegative) { "booking.arrival-otp.resend-cooldown cannot be negative" }
        require(maxSends > 0) { "booking.arrival-otp.max-sends must be positive" }
    }
}
