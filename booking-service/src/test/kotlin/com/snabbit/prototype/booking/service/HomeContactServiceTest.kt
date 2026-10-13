package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.dto.request.UpdateHomeContactRequest
import com.snabbit.prototype.booking.dto.response.BookerContactSnapshot
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.HomeContactValidationException
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.repository.BookingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeContactServiceTest {
    private val bookings = mockk<BookingRepository>()
    private val contacts = mockk<UserContactClient>()
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val service = HomeContactService(bookings, contacts, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `SELF uses authoritative booker details and masks the response`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking
        every { contacts.contact(booking.customerId) } returns
            BookerContactSnapshot(booking.customerId, "+919876543210", "  Siddhesh Rao  ")

        val result = service.update(
            booking.id,
            booking.customerId,
            UpdateHomeContactRequest("SELF", "Ignored Name", "+919999999999"),
        )

        assertEquals("Siddhesh Rao", booking.homeContactName)
        assertEquals("+919876543210", booking.homeContactPhoneE164)
        assertEquals(true, booking.homeContactIsBooker)
        assertEquals("••••••3210", result.maskedPhone)
        assertFalse(result.maskedPhone.contains("+91"))
    }

    @Test
    fun `blank request defaults to SELF`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking
        every { contacts.contact(booking.customerId) } returns
            BookerContactSnapshot(booking.customerId, "+919876543210", "Booker")

        val result = service.update(booking.id, booking.customerId, null)

        assertTrue(result.isBooker)
        assertEquals("Booker", result.name)
    }

    @Test
    fun `OTHER validates and normalizes its snapshot without creating a user`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking

        val result = service.update(
            booking.id,
            booking.customerId,
            UpdateHomeContactRequest("OTHER", "  Asha D'Souza  ", "09876543210"),
        )

        assertEquals("Asha D'Souza", booking.homeContactName)
        assertEquals("+919876543210", booking.homeContactPhoneE164)
        assertEquals(false, booking.homeContactIsBooker)
        assertEquals("••••••3210", result.maskedPhone)
        verify(exactly = 0) { contacts.contact(any()) }
    }

    @Test
    fun `OTHER reports a field error for an invalid name`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking

        val error = assertThrows(HomeContactValidationException::class.java) {
            service.update(
                booking.id,
                booking.customerId,
                UpdateHomeContactRequest("OTHER", "A1", "9876543210"),
            )
        }

        assertEquals("name", error.field)
    }

    @Test
    fun `OTHER reports a field error for an invalid phone`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking

        val error = assertThrows(HomeContactValidationException::class.java) {
            service.update(
                booking.id,
                booking.customerId,
                UpdateHomeContactRequest("OTHER", "Asha", "5876543210"),
            )
        }

        assertEquals("phone", error.field)
    }

    @Test
    fun `another customer cannot change the home contact`() {
        val booking = booking()
        every { bookings.findForUpdate(booking.id) } returns booking

        assertThrows(CustomerAccessDenied::class.java) {
            service.update(booking.id, UUID.randomUUID(), UpdateHomeContactRequest())
        }

        verify(exactly = 0) { contacts.contact(any()) }
    }

    @Test
    fun `confirmation freezes the saved contact`() {
        val booking = booking()
        booking.updateHomeContact("Booker", "+919876543210", true, now)
        booking.awaitPayment(now)
        booking.confirm("wallet:payment", now)
        every { bookings.findForUpdate(booking.id) } returns booking

        assertEquals(now, booking.homeContactFrozenAt)
        assertThrows(InvalidBookingState::class.java) {
            service.update(booking.id, booking.customerId, UpdateHomeContactRequest())
        }
    }

    @Test
    fun `payment fallback resolves SELF only once`() {
        val booking = booking().apply { awaitPayment(now) }
        every { bookings.findForUpdate(booking.id) } returns booking
        every { contacts.contact(booking.customerId) } returns
            BookerContactSnapshot(booking.customerId, "+919876543210", "Booker")

        service.ensureResolved(booking.id, booking.customerId)
        service.ensureResolved(booking.id, booking.customerId)

        verify(exactly = 1) { contacts.contact(booking.customerId) }
    }

    private fun booking() = Booking(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.SCHEDULED,
        status = BookingStatus.AWAITING_WORKER,
        durationMinutes = 60,
        quotedTotalMinor = 10_000,
        createdAt = now,
        updatedAt = now,
    )
}
