package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.CancellationContext
import com.snabbit.prototype.booking.dto.response.CancellationQuoteSnapshot
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.matching.MatchingClients
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

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
