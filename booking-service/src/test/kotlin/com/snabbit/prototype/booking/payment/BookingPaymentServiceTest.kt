package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.application.BookingLifecycleService
import com.snabbit.prototype.booking.application.PaymentQuoteSnapshot
import com.snabbit.prototype.booking.support.InvalidBookingState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BookingPaymentServiceTest {
    private val bookings = mockk<BookingLifecycleService>()
    private val client = mockk<BookingPaymentClient>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = BookingPaymentService(bookings, client, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `starts wallet payment only for an active authoritative quote`() {
        val command = command()
        val expected = payment(command)
        every { bookings.paymentQuote(command.bookingId) } returns quote(command.bookingId)
        every { client.start(command) } returns expected

        val result = service.start(command)

        assertEquals(expected, result)
        verify(exactly = 1) { client.start(command) }
    }

    @Test
    fun `expired hold never reaches wallet service`() {
        val command = command()
        every { bookings.paymentQuote(command.bookingId) } returns quote(command.bookingId).copy(holdExpiresAt = now)

        assertThrows(InvalidBookingState::class.java) { service.start(command) }

        verify(exactly = 0) { client.start(any()) }
    }

    private fun command() = StartBookingPaymentCommand(
        UUID.randomUUID(),
        UUID.randomUUID(),
        true,
        "payment-once",
    )

    private fun quote(bookingId: UUID) = PaymentQuoteSnapshot(
        bookingId,
        UUID.randomUUID(),
        "AWAITING_PAYMENT",
        15_000,
        "INR",
        now.plusSeconds(300),
    )

    private fun payment(command: StartBookingPaymentCommand) = BookingPaymentSnapshot(
        UUID.randomUUID(),
        command.bookingId,
        command.walletId,
        15_000,
        10_000,
        5_000,
        "PENDING",
        "order_123",
        now.plusSeconds(300),
    )
}
