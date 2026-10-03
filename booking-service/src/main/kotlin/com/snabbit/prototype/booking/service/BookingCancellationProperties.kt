package com.snabbit.prototype.booking.service

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cancellation")
data class BookingCancellationProperties(
    val lateWindow: Duration,
    val earlyFeeMinor: Long,
    val lateFeeMinor: Long,
    val plusFeeDiscountPercent: Int,
) {
    init {
        require(!lateWindow.isNegative && !lateWindow.isZero) { "Late cancellation window must be positive" }
        require(earlyFeeMinor >= 0 && lateFeeMinor >= 0) { "Cancellation fees cannot be negative" }
        require(plusFeeDiscountPercent in 0..100) { "Plus fee discount percent must be between 0 and 100" }
    }
}
