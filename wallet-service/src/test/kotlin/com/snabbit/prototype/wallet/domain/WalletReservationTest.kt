package com.snabbit.prototype.wallet.domain

import com.snabbit.prototype.wallet.domain.enums.ReservationStatus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WalletReservationTest {
    private val now = Instant.parse("2026-10-02T04:00:00Z")

    @Test
    fun `captures an active reservation before expiry`() {
        val reservation = reservation(now.plusSeconds(60))

        reservation.capture(now)

        assertEquals(ReservationStatus.CAPTURED, reservation.status)
    }

    @Test
    fun `does not capture an expired reservation`() {
        val reservation = reservation(now)

        assertThrows(IllegalStateException::class.java) { reservation.capture(now) }
    }

    private fun reservation(expiresAt: Instant) = WalletReservation(
        id = UUID.randomUUID(),
        walletId = UUID.randomUUID(),
        bookingId = UUID.randomUUID(),
        amountMinor = 5_000,
        status = ReservationStatus.ACTIVE,
        expiresAt = expiresAt,
        createdAt = now,
        updatedAt = now,
    )
}
