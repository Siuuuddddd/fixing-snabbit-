package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingDevSeedProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.AssignmentType
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import com.snabbit.prototype.platform.DevSeedWorkerIds
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable

class DevSeedBookingAutomationTest {
    private val offers = mockk<WorkerOfferRepository>()
    private val holds = mockk<SlotHoldRepository>(relaxed = true)
    private val bookings = mockk<BookingRepository>()
    private val lifecycle = mockk<BookingLifecycleService>()
    private val now = Instant.parse("2026-10-14T06:00:00Z")
    private val properties = BookingDevSeedProperties(
        enabled = true,
        autoAcceptOffers = true,
        autoAcceptDelayMs = 1_500,
        autoProgress = true,
        autoProgressEnRouteDelayMs = 1_500,
        autoProgressArrivalDelayMs = 3_000,
    )
    private val service = DevSeedBookingAutomation(
        offers,
        holds,
        bookings,
        lifecycle,
        properties,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `auto accept uses configured cutoff and accepts seeded workers only`() {
        val seeded = offer(DevSeedWorkerIds.SAVITA)
        val real = offer(UUID.randomUUID())
        every {
            offers.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                WorkerOfferStatus.OFFERED,
                now.minusMillis(1_500),
                any<Pageable>(),
            )
        } returns listOf(seeded, real)
        every { holds.findById(seeded.slotHoldId) } returns Optional.of(
            mockk { every { assignmentType } returns AssignmentType.PREFERRED },
        )
        every { lifecycle.acceptOffer(seeded.bookingId, seeded.id, seeded.workerId) } returns mockk()

        service.autoAcceptOffers()

        verify(exactly = 1) { lifecycle.acceptOffer(seeded.bookingId, seeded.id, seeded.workerId) }
        verify(exactly = 0) { lifecycle.acceptOffer(real.bookingId, real.id, real.workerId) }
    }

    @Test
    fun `auto progress uses normal transitions and stops at arrived for OTP`() {
        val booking = booking()
        val assigned = hold(booking, 0, JobStatus.ASSIGNED)
        val enRoute = hold(booking, 1, JobStatus.EN_ROUTE)
        every {
            holds.findByWorkerIdInAndStatusAndJobStatusIn(
                DevSeedWorkerIds.all,
                SlotHoldStatus.CONFIRMED,
                listOf(JobStatus.ASSIGNED, JobStatus.EN_ROUTE),
            )
        } returns listOf(assigned, enRoute)
        every { bookings.findById(booking.id) } returns Optional.of(booking)
        every { lifecycle.workerEnRoute(booking.id, assigned.visitIndex, assigned.workerId) } returns mockk()
        every { lifecycle.workerArrive(booking.id, enRoute.visitIndex, enRoute.workerId, any()) } returns mockk()

        service.autoProgressVisits()

        verify(exactly = 1) { lifecycle.workerEnRoute(booking.id, assigned.visitIndex, assigned.workerId) }
        verify(exactly = 1) { lifecycle.workerArrive(booking.id, enRoute.visitIndex, enRoute.workerId, any()) }
        verify(exactly = 0) { lifecycle.workerStart(any(), any(), any()) }
        verify(exactly = 0) { lifecycle.workerComplete(any(), any(), any()) }
    }

    private fun offer(workerId: UUID) = WorkerOffer(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        workerId,
        WorkerOfferStatus.OFFERED,
        now.plusSeconds(60),
        null,
        now.minusSeconds(2),
    )

    private fun booking() = Booking(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.MULTI_DAY,
        status = BookingStatus.CONFIRMED,
        durationMinutes = 60,
        quotedTotalMinor = 36_000,
        serviceLatitude = BigDecimal("19.076090"),
        serviceLongitude = BigDecimal("72.877426"),
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(60),
        confirmedAt = now.minusSeconds(10),
    )

    private fun hold(booking: Booking, index: Int, jobStatus: JobStatus) = SlotHold(
        id = UUID.randomUUID(),
        bookingId = booking.id,
        visitIndex = index,
        customerId = booking.customerId,
        workerId = DevSeedWorkerIds.SAVITA,
        serviceId = booking.serviceId,
        startsAt = now.plusSeconds(3_600),
        endsAt = now.plusSeconds(7_200),
        status = SlotHoldStatus.CONFIRMED,
        expiresAt = now.plusSeconds(300),
        createdAt = now.minusSeconds(60),
        jobStatus = jobStatus,
    )
}
