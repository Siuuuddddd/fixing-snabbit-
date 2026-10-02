package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.domain.PlusPassStatus
import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientResponseException
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ExpiredPassReservation(val passId: UUID, val bookingId: UUID, val userId: UUID)

data class PlusPassBookingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val plusPassId: UUID?,
    val holdExpiresAt: Instant,
    val confirmedAt: Instant?,
)

@Service
class PlusReconciliationQuery(private val passes: PlusPassRepository) {
    @Transactional(readOnly = true)
    fun expired(now: Instant): List<ExpiredPassReservation> =
        passes.findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAt(PlusPassStatus.HELD, now)
            .mapNotNull { pass ->
                pass.heldForBookingId?.let { ExpiredPassReservation(pass.id, it, pass.userId) }
            }
}

@Component
class PlusReconciliationBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): PlusPassBookingContext? = try {
        client.get()
            .uri("/internal/v1/bookings/{bookingId}/plus-pass-context", bookingId)
            .retrieve()
            .body(PlusPassBookingContext::class.java)
            ?: error("Booking Service returned an empty Plus pass context")
    } catch (error: RestClientResponseException) {
        if (error.statusCode.value() == 404) null else throw error
    }
}

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
