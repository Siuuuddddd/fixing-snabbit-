package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WorkerOfferTest {
    private val now = Instant.parse("2026-10-02T04:00:00Z")

    @Test
    fun `accepts an open offer before its deadline`() {
        val offer = offer(now.plusSeconds(60))

        offer.accept(now)

        assertEquals(WorkerOfferStatus.ACCEPTED, offer.status)
        assertEquals(now, offer.respondedAt)
    }

    @Test
    fun `does not accept an expired offer`() {
        val offer = offer(now)

        assertThrows(IllegalStateException::class.java) { offer.accept(now) }
    }

    private fun offer(expiresAt: Instant) = WorkerOffer(
        id = UUID.randomUUID(),
        bookingId = UUID.randomUUID(),
        slotHoldId = UUID.randomUUID(),
        workerId = UUID.randomUUID(),
        status = WorkerOfferStatus.OFFERED,
        expiresAt = expiresAt,
        createdAt = now,
    )
}
