package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.ArrivalOtpProperties
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.VisitArrivalChallenge
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.exception.ArrivalOtpAccessDenied
import com.snabbit.prototype.booking.exception.ArrivalOtpConflict
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.otp.ArrivalOtpProvider
import com.snabbit.prototype.booking.otp.ArrivalOtpCheckResult
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.BookingType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class ArrivalOtpCommandServiceTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val holds = mockk<SlotHoldRepository>()
    private val challenges = mockk<VisitArrivalChallengeRepository>()
    private val bookings = mockk<BookingRepository>()
    private val provider = mockk<ArrivalOtpProvider>()
    private val rateLimiter = mockk<ArrivalOtpVerificationRateLimiter>(relaxed = true)
    private val outbox = mockk<OutboxWriter>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"), LocalTime.of(8, 0), LocalTime.of(19, 0),
        Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(30), 100,
        arrivalOtp = ArrivalOtpProperties(resendCooldown = Duration.ofSeconds(30), maxSends = 3),
    )
    private val service = ArrivalOtpCommandService(
        holds, challenges, bookings, provider, rateLimiter, outbox, properties, events,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `correct code atomically verifies challenge and starts visit`() {
        val hold = arrivedHold()
        val booking = confirmedBooking(hold)
        val challenge = challenge(hold)
        every { bookings.findForUpdate(booking.id) } returns booking
        every { holds.findVisitForUpdate(hold.bookingId, hold.visitIndex) } returns hold
        every { challenges.findBySlotHoldIdForUpdate(hold.id) } returns challenge
        every { provider.check(challenge.destinationPhoneE164, "123456") } returns ArrivalOtpCheckResult.APPROVED

        val result = service.verifyAndStart(booking.id, hold.visitIndex, hold.workerId, "123456")

        assertEquals(ArrivalChallengeStatus.VERIFIED, challenge.status)
        assertEquals(JobStatus.IN_PROGRESS, hold.jobStatus)
        assertEquals("VERIFIED", result.otpStatus)
        verify(exactly = 1) { outbox.append(booking.id, "ServiceStarted", any(), now) }
    }

    @Test
    fun `repeated correct verification is idempotent and emits service start once`() {
        val hold = arrivedHold()
        val booking = confirmedBooking(hold)
        val challenge = challenge(hold)
        every { bookings.findForUpdate(booking.id) } returns booking
        every { holds.findVisitForUpdate(hold.bookingId, hold.visitIndex) } returns hold
        every { challenges.findBySlotHoldIdForUpdate(hold.id) } returns challenge
        every { provider.check(any(), any()) } returns ArrivalOtpCheckResult.APPROVED

        service.verifyAndStart(booking.id, hold.visitIndex, hold.workerId, "123456")
        service.verifyAndStart(booking.id, hold.visitIndex, hold.workerId, "123456")

        verify(exactly = 1) { provider.check(any(), any()) }
        verify(exactly = 1) { outbox.append(booking.id, "ServiceStarted", any(), now) }
    }

    @Test
    fun `wrong codes increment attempts and lock at the limit`() {
        val hold = arrivedHold()
        val booking = confirmedBooking(hold)
        val challenge = challenge(hold).apply { attemptCount = properties.arrivalOtp.maxAttempts - 1 }
        every { bookings.findForUpdate(booking.id) } returns booking
        every { holds.findVisitForUpdate(hold.bookingId, hold.visitIndex) } returns hold
        every { challenges.findBySlotHoldIdForUpdate(hold.id) } returns challenge
        every { provider.check(any(), any()) } returns ArrivalOtpCheckResult.WRONG_CODE

        val error = assertThrows(ArrivalOtpConflict::class.java) {
            service.verifyAndStart(booking.id, hold.visitIndex, hold.workerId, "999999")
        }

        assertEquals("ARRIVAL_OTP_LOCKED", error.code)
        assertEquals(ArrivalChallengeStatus.LOCKED, challenge.status)
        verify(exactly = 1) { outbox.append(booking.id, "VisitOtpLocked", any(), now) }
    }

    @Test
    fun `assigned arrived worker can request resend after cooldown`() {
        val hold = arrivedHold()
        val challenge = challenge(hold).apply { sendCount = 1; lastSentAt = now.minusSeconds(31) }
        every { holds.findVisitForUpdate(hold.bookingId, hold.visitIndex) } returns hold
        every { challenges.findBySlotHoldIdForUpdate(hold.id) } returns challenge

        val result = service.resend(hold.bookingId, hold.visitIndex, hold.workerId)

        assertEquals(ArrivalChallengeStatus.PENDING, challenge.status)
        assertEquals("OTP_SENT", result.otpStatus)
        verify { events.publishEvent(ArrivalChallengeCreatedEvent(challenge.id)) }
    }

    @Test
    fun `resend enforces worker ownership and cooldown`() {
        val hold = arrivedHold()
        every { holds.findVisitForUpdate(hold.bookingId, hold.visitIndex) } returns hold
        assertThrows(ArrivalOtpAccessDenied::class.java) {
            service.resend(hold.bookingId, hold.visitIndex, UUID.randomUUID())
        }

        val challenge = challenge(hold).apply { sendCount = 1; lastSentAt = now.minusSeconds(5) }
        every { challenges.findBySlotHoldIdForUpdate(hold.id) } returns challenge
        val error = assertThrows(ArrivalOtpConflict::class.java) {
            service.resend(hold.bookingId, hold.visitIndex, hold.workerId)
        }
        assertEquals("ARRIVAL_OTP_RESEND_COOLDOWN", error.code)
    }

    private fun arrivedHold() = SlotHold(
        UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        now, now.plusSeconds(3600), SlotHoldStatus.CONFIRMED, now, now, jobStatus = JobStatus.ARRIVED,
    )

    private fun challenge(hold: SlotHold) = VisitArrivalChallenge(
        UUID.randomUUID(), hold.bookingId, hold.visitIndex, hold.id, hold.workerId, "+919876543210",
        providerVerificationRef = "provider-ref", status = ArrivalChallengeStatus.SENT,
        expiresAt = now.plusSeconds(300), createdAt = now.minusSeconds(60), updatedAt = now.minusSeconds(31),
    )

    private fun confirmedBooking(hold: SlotHold) = Booking(
        hold.bookingId, hold.customerId, hold.serviceId, BookingType.SCHEDULED, BookingStatus.CONFIRMED,
        60, createdAt = now.minusSeconds(3600), updatedAt = now,
        homeContactName = "Booker", homeContactPhoneE164 = "+919876543210",
        homeContactIsBooker = true, homeContactFrozenAt = now.minusSeconds(300),
    )
}
