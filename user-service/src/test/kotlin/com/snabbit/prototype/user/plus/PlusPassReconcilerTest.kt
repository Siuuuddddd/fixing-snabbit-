package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.ExpiredPassReservation

import com.snabbit.prototype.user.dto.response.PlusPassBookingContext

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PlusPassReconcilerTest {
    private val query = mockk<PlusReconciliationQuery>()
    private val bookings = mockk<PlusReconciliationBookingClient>()
    private val plus = mockk<PlusService>(relaxed = true)
    private val now = Instant.parse("2026-10-02T04:10:00Z")
    private val reconciler = PlusPassReconciler(query, bookings, plus, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `confirmed booking consumes an expired reservation instead of releasing it`() {
        val reservation = reservation()
        every { query.expired(now) } returns listOf(reservation)
        every { bookings.context(reservation.bookingId) } returns context(reservation, "CONFIRMED", now.minusSeconds(20))

        reconciler.reconcileExpiredReservations()

        verify(exactly = 1) { plus.reconcileConfirmed(reservation.bookingId, reservation.passId, now.minusSeconds(20)) }
        verify(exactly = 0) { plus.releasePass(any()) }
    }

    @Test
    fun `missing booking releases reservation left by a crashed booking request`() {
        val reservation = reservation()
        every { query.expired(now) } returns listOf(reservation)
        every { bookings.context(reservation.bookingId) } returns null

        reconciler.reconcileExpiredReservations()

        verify(exactly = 1) { plus.releasePass(reservation.bookingId) }
    }

    private fun reservation() = ExpiredPassReservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    private fun context(reservation: ExpiredPassReservation, status: String, confirmedAt: Instant?) =
        PlusPassBookingContext(
            reservation.bookingId,
            reservation.userId,
            status,
            reservation.passId,
            now.minusSeconds(30),
            confirmedAt,
        )
}
