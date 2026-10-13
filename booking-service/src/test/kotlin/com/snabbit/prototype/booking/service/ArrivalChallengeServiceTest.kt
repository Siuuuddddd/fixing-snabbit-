package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.ArrivalOtpProperties
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.VisitArrivalChallenge
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.exception.ArrivalOtpRequired
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class ArrivalChallengeServiceTest {
    private val repository = mockk<VisitArrivalChallengeRepository>()
    private val homeContacts = mockk<HomeContactService>(relaxed = true)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"),
        LocalTime.of(8, 0),
        LocalTime.of(19, 0),
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        100,
        arrivalOtp = ArrivalOtpProperties(required = true),
    )
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = ArrivalChallengeService(repository, homeContacts, properties, events)
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Test
    fun `validated arrival creates one pending challenge without OTP digits`() {
        val booking = confirmedBooking()
        val hold = arrivedHold(booking)
        every { repository.findBySlotHoldId(hold.id) } returns null
        every { repository.save(any()) } answers { firstArg() }

        val result = service.createForValidatedArrival(booking, hold, now)

        assertEquals(ArrivalChallengeStatus.PENDING, result.status)
        assertEquals(0, result.attemptCount)
        assertEquals(0, result.sendCount)
        assertEquals(null, result.providerVerificationRef)
        assertEquals(booking.homeContactPhoneE164, result.destinationPhoneE164)
        assertEquals(now.plusSeconds(300), result.expiresAt)
    }

    @Test
    fun `repeat arrival reuses the existing challenge`() {
        val booking = confirmedBooking()
        val hold = arrivedHold(booking)
        val existing = challenge(booking, hold, ArrivalChallengeStatus.PENDING)
        every { repository.findBySlotHoldId(hold.id) } returns existing

        assertEquals(existing, service.createForValidatedArrival(booking, hold, now))

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `challenge cannot be created before validated arrival`() {
        val booking = confirmedBooking()
        val hold = arrivedHold(booking).apply { jobStatus = JobStatus.ASSIGNED }
        every { repository.findBySlotHoldId(hold.id) } returns null

        assertThrows(InvalidBookingState::class.java) {
            service.createForValidatedArrival(booking, hold, now)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `required flag gates start until the assigned visit challenge is verified`() {
        val booking = confirmedBooking()
        val hold = arrivedHold(booking)
        every { repository.findBySlotHoldIdForUpdate(hold.id) } returns
            challenge(booking, hold, ArrivalChallengeStatus.SENT)

        assertThrows(ArrivalOtpRequired::class.java) {
            service.requireVerified(booking.id, hold.visitIndex, hold)
        }
    }

    @Test
    fun `verified challenge permits start and cancellation invalidates it`() {
        val booking = confirmedBooking()
        val hold = arrivedHold(booking)
        val challenge = challenge(booking, hold, ArrivalChallengeStatus.VERIFIED)
        every { repository.findBySlotHoldIdForUpdate(hold.id) } returns challenge

        service.requireVerified(booking.id, hold.visitIndex, hold)
        service.cancelForVisit(hold, now)

        assertEquals(ArrivalChallengeStatus.CANCELLED, challenge.status)
    }

    private fun confirmedBooking() = Booking(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.SCHEDULED,
        status = BookingStatus.CONFIRMED,
        durationMinutes = 60,
        quotedTotalMinor = 10_000,
        createdAt = now,
        updatedAt = now,
        homeContactName = "Booker",
        homeContactPhoneE164 = "+919876543210",
        homeContactIsBooker = true,
        homeContactUpdatedAt = now,
        homeContactFrozenAt = now,
    )

    private fun arrivedHold(booking: Booking) = SlotHold(
        id = UUID.randomUUID(),
        bookingId = booking.id,
        visitIndex = 0,
        customerId = booking.customerId,
        workerId = UUID.randomUUID(),
        serviceId = booking.serviceId,
        startsAt = now,
        endsAt = now.plusSeconds(3600),
        status = SlotHoldStatus.CONFIRMED,
        expiresAt = now,
        createdAt = now,
        jobStatus = JobStatus.ARRIVED,
    )

    private fun challenge(
        booking: Booking,
        hold: SlotHold,
        status: ArrivalChallengeStatus,
    ) = VisitArrivalChallenge(
        id = UUID.randomUUID(),
        bookingId = booking.id,
        visitIndex = hold.visitIndex,
        slotHoldId = hold.id,
        assignedWorkerId = hold.workerId,
        destinationPhoneE164 = requireNotNull(booking.homeContactPhoneE164),
        status = status,
        expiresAt = now.plusSeconds(300),
        createdAt = now,
        updatedAt = now,
    )
}
