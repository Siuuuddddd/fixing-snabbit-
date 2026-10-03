package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.dto.response.CancellationQuoteSnapshot

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

@Component
class BookingCancellationPolicy(private val properties: BookingCancellationProperties) {
    fun quote(
        bookingId: UUID,
        chargedMinor: Long,
        earliestStart: Instant,
        plusActive: Boolean,
        now: Instant,
    ): CancellationQuoteSnapshot {
        require(chargedMinor > 0) { "Cancellation quote must be based on a positive charge" }
        if (!earliestStart.isAfter(now)) throw InvalidBookingState("Booking start time has passed")
        val late = Duration.between(now, earliestStart) < properties.lateWindow
        val baseFee = if (late) properties.lateFeeMinor else properties.earlyFeeMinor
        val reducedFee = if (plusActive) {
            baseFee * (100 - properties.plusFeeDiscountPercent) / 100
        } else {
            baseFee
        }
        val fee = min(chargedMinor, reducedFee)
        return CancellationQuoteSnapshot(
            bookingId, plusActive, fee, chargedMinor - fee,
            if (late) "LATE" else "EARLY", now,
        )
    }
}
