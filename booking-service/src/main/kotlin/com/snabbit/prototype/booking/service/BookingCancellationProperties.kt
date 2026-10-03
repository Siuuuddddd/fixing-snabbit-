package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.matching.MatchingClients
import com.snabbit.prototype.booking.support.CustomerAccessDenied
import com.snabbit.prototype.booking.support.InvalidBookingState
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.min

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
