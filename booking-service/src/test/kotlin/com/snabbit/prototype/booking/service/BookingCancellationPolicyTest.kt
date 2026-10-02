package com.snabbit.prototype.booking.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BookingCancellationPolicyTest {
    private val policy = BookingCancellationPolicy(
        BookingCancellationProperties(Duration.ofHours(2), 5_000, 10_000, 50),
    )
    private val now = Instant.parse("2026-10-02T04:00:00Z")

    @Test
    fun `Plus halves the early cancellation fee`() {
        val standard = policy.quote(UUID.randomUUID(), 42_000, now.plusSeconds(10_800), false, now)
        val plus = policy.quote(UUID.randomUUID(), 42_000, now.plusSeconds(10_800), true, now)

        assertEquals(5_000, standard.cancellationFeeMinor)
        assertEquals(2_500, plus.cancellationFeeMinor)
        assertEquals("EARLY", plus.policyBand)
    }

    @Test
    fun `late cancellation uses higher fee and never exceeds the charge`() {
        val quote = policy.quote(UUID.randomUUID(), 7_000, now.plusSeconds(3_600), false, now)

        assertEquals(7_000, quote.cancellationFeeMinor)
        assertEquals(0, quote.refundAmountMinor)
        assertEquals("LATE", quote.policyBand)
    }
}
