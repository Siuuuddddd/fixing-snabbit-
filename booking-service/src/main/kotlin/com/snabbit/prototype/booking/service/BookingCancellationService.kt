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

@Service
class BookingCancellationService(
    private val lifecycle: BookingLifecycleService,
    private val clients: MatchingClients,
    private val policy: BookingCancellationPolicy,
    private val clock: Clock,
) {
    suspend fun quote(bookingId: UUID, customerId: UUID): CancellationQuoteSnapshot {
        val context = requireCancellableOwner(bookingId, customerId)
        val household = clients.household(customerId)
        return policy.quote(
            bookingId, context.quotedTotalMinor, context.earliestVisitStartsAt,
            household.plusActive, clock.instant(),
        )
    }

    suspend fun cancel(bookingId: UUID, customerId: UUID, reason: String?): BookingSnapshot {
        val context = lifecycle.cancellationContext(bookingId)
        if (context.customerId != customerId) throw CustomerAccessDenied()
        if (context.status == "CANCELLED" && context.cancellationType == "CUSTOMER") {
            return lifecycle.get(bookingId)
        }
        if (context.status != "CONFIRMED" || !context.allVisitsUnstarted) {
            throw InvalidBookingState("Only a confirmed booking with unstarted visits can be cancelled")
        }
        val household = clients.household(customerId)
        return lifecycle.cancelConfirmed(bookingId, customerId, household.plusActive, reason, policy)
    }

    private fun requireCancellableOwner(bookingId: UUID, customerId: UUID): CancellationContext {
        val context = lifecycle.cancellationContext(bookingId)
        if (context.customerId != customerId) throw CustomerAccessDenied()
        if (context.status != "CONFIRMED" || !context.allVisitsUnstarted) {
            throw InvalidBookingState("Only a confirmed booking with unstarted visits can be cancelled")
        }
        return context
    }
}
