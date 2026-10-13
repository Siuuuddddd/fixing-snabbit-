package com.snabbit.prototype.booking.config

import java.time.Duration

data class ArrivalOtpProperties(
    val required: Boolean = true,
    val expiry: Duration = Duration.ofMinutes(5),
    val maxAttempts: Int = 5,
    val resendCooldown: Duration = Duration.ofSeconds(30),
    val maxSends: Int = 3,
    val verificationRateWindow: Duration = Duration.ofMinutes(1),
    val maxVerificationRequestsPerVisit: Int = 10,
    val maxVerificationRequestsPerWorker: Int = 20,
) {
    init {
        require(expiry > Duration.ZERO) { "booking.arrival-otp.expiry must be positive" }
        require(maxAttempts > 0) { "booking.arrival-otp.max-attempts must be positive" }
        require(!resendCooldown.isNegative) { "booking.arrival-otp.resend-cooldown cannot be negative" }
        require(maxSends > 0) { "booking.arrival-otp.max-sends must be positive" }
        require(verificationRateWindow > Duration.ZERO) { "booking.arrival-otp.verification-rate-window must be positive" }
        require(maxVerificationRequestsPerVisit > 0) { "booking.arrival-otp.max-verification-requests-per-visit must be positive" }
        require(maxVerificationRequestsPerWorker > 0) { "booking.arrival-otp.max-verification-requests-per-worker must be positive" }
    }
}
