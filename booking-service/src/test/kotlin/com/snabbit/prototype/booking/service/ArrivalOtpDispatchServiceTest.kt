package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.ArrivalOtpProperties
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.VisitArrivalChallenge
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.dto.response.WorkerPrivateContactSnapshot
import com.snabbit.prototype.booking.otp.ArrivalOtpProvider
import com.snabbit.prototype.booking.otp.ArrivalOtpProviderUnavailable
import com.snabbit.prototype.booking.otp.ArrivalOtpStartResult
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ArrivalOtpDispatchServiceTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val repository = mockk<VisitArrivalChallengeRepository>()
    private val workers = mockk<WorkerContactClient>()
    private val provider = mockk<ArrivalOtpProvider>()
    private val outbox = mockk<OutboxWriter>(relaxed = true)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"), LocalTime.of(8, 0), LocalTime.of(19, 0),
        Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(30), 100,
        arrivalOtp = ArrivalOtpProperties(maxSends = 3),
    )
    private val service = ArrivalOtpDispatchService(
        repository, workers, provider, outbox, properties, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `successful dispatch persists provider reference and emits identifiers only`() {
        val challenge = challenge()
        every { repository.findByIdForUpdate(challenge.id) } returns challenge
        every { workers.contact(challenge.assignedWorkerId) } returns
            WorkerPrivateContactSnapshot(challenge.assignedWorkerId, "+919000000001")
        every { provider.start(challenge.destinationPhoneE164) } returns ArrivalOtpStartResult("verification-ref")
        val payloads = mutableListOf<Map<String, Any>>()
        every { outbox.append(any(), any(), capture(payloads), any()) } returns Unit

        service.dispatch(challenge.id)

        assertEquals(ArrivalChallengeStatus.SENT, challenge.status)
        assertEquals("verification-ref", challenge.providerVerificationRef)
        assertEquals(1, challenge.sendCount)
        assertEquals(setOf("bookingId", "visitIndex", "workerId", "otpStatus"), payloads.single().keys)
        assertFalse(payloads.single().toString().contains(challenge.destinationPhoneE164))
    }

    @Test
    fun `provider failures stop after configured sends and publish terminal failure`() {
        val challenge = challenge()
        every { repository.findByIdForUpdate(challenge.id) } returns challenge
        every { workers.contact(challenge.assignedWorkerId) } returns
            WorkerPrivateContactSnapshot(challenge.assignedWorkerId, "+919000000001")
        every { provider.start(any()) } throws ArrivalOtpProviderUnavailable()

        repeat(3) { service.dispatch(challenge.id) }

        assertEquals(ArrivalChallengeStatus.DELIVERY_FAILED, challenge.status)
        assertEquals(3, challenge.sendCount)
        verify(exactly = 1) { outbox.append(challenge.bookingId, "ArrivalOtpDeliveryFailed", any(), now) }
    }

    @Test
    fun `worker cannot receive the home contact arrival code`() {
        val challenge = challenge()
        every { repository.findByIdForUpdate(challenge.id) } returns challenge
        every { workers.contact(challenge.assignedWorkerId) } returns
            WorkerPrivateContactSnapshot(challenge.assignedWorkerId, challenge.destinationPhoneE164)

        service.dispatch(challenge.id)

        assertEquals(ArrivalChallengeStatus.DELIVERY_FAILED, challenge.status)
        assertEquals(3, challenge.sendCount)
        verify(exactly = 0) { provider.start(any()) }
        verify(exactly = 1) { outbox.append(challenge.bookingId, "ArrivalOtpDeliveryFailed", any(), now) }
    }

    private fun challenge() = VisitArrivalChallenge(
        UUID.randomUUID(), UUID.randomUUID(), 2, UUID.randomUUID(), UUID.randomUUID(),
        "+919876543210", status = ArrivalChallengeStatus.PENDING,
        expiresAt = now.plusSeconds(300), createdAt = now, updatedAt = now,
    )
}
