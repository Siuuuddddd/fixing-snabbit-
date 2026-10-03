package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.ExpiredPassReservation
import java.time.Clock
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PlusPassReconciler(
    private val query: PlusReconciliationQuery,
    private val bookings: PlusReconciliationBookingClient,
    private val plus: PlusService,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${plus.reconciliation-delay:PT1M}")
    fun reconcileExpiredReservations() {
        val now = clock.instant()
        query.expired(now).forEach { reservation ->
            runCatching { reconcileOne(reservation, now) }
                .onFailure { error -> logger.warn("Could not reconcile Plus pass {}", reservation.passId, error) }
        }
    }

    private fun reconcileOne(reservation: ExpiredPassReservation, now: Instant) {
        val booking = bookings.context(reservation.bookingId)
        if (booking == null) {
            plus.releasePass(reservation.bookingId)
            return
        }
        if (booking.customerId != reservation.userId || booking.plusPassId != reservation.passId) {
            error("Booking and Plus pass reservation do not agree")
        }
        when (booking.status) {
            "CONFIRMED", "COMPLETED" -> plus.reconcileConfirmed(
                booking.bookingId,
                reservation.passId,
                checkNotNull(booking.confirmedAt) { "Confirmed booking has no confirmation timestamp" },
            )
            "CANCELLED", "EXPIRED", "WORKER_UNAVAILABLE" -> plus.releasePass(booking.bookingId)
            else -> if (!booking.holdExpiresAt.isAfter(now)) plus.releasePass(booking.bookingId)
        }
    }
}
