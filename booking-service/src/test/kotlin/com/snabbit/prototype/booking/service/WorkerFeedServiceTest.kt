package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.dto.response.WorkerOfferFeedItem

import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus

import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus

import com.snabbit.prototype.booking.domain.enums.JobStatus

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.enums.BookingStatus

import com.snabbit.prototype.booking.catalog.ServiceOfferingRepository
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.ServiceOffering
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WorkerFeedServiceTest {
    private val offers = mockk<WorkerOfferRepository>()
    private val holds = mockk<SlotHoldRepository>()
    private val bookings = mockk<BookingRepository>()
    private val services = mockk<ServiceOfferingRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val workerId = UUID.randomUUID()
    private val serviceOffering = ServiceOffering(UUID.randomUUID(), "HOME_CLEANING", "Home cleaning", 20_000, true)
    private val feed = WorkerFeedService(offers, holds, bookings, services, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `pending offer contains decision context without customer or location fields`() {
        val booking = booking()
        val hold = hold(booking, now.plusSeconds(3_600), SlotHoldStatus.HELD, null)
        val offer = WorkerOffer(
            UUID.randomUUID(),
            booking.id,
            hold.id,
            workerId,
            WorkerOfferStatus.OFFERED,
            now.plusSeconds(60),
            null,
            now,
        )
        every {
            offers.findByWorkerIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
                workerId,
                WorkerOfferStatus.OFFERED,
                now,
                any<Pageable>(),
            )
        } returns listOf(offer)
        every { holds.findAllById(any<Iterable<UUID>>()) } returns listOf(hold)
        every { bookings.findAllById(any<Iterable<UUID>>()) } returns listOf(booking)
        every { services.findAllById(any<Iterable<UUID>>()) } returns listOf(serviceOffering)

        val result = feed.pendingOffers(workerId).single()

        assertEquals("HOME_CLEANING", result.serviceCode)
        assertEquals(booking.requirements, result.requirements)
        assertEquals(60, result.durationMinutes)
        assertEquals(
            setOf(
                "offerId", "bookingId", "visitIndex", "serviceId", "serviceCode", "serviceName",
                "startsAt", "endsAt", "durationMinutes", "offerExpiresAt", "requirements",
            ),
            WorkerOfferFeedItem::class.java.declaredFields.map { it.name }.toSet(),
        )
    }

    @Test
    fun `assigned job hides exact location until two hours before start`() {
        val booking = booking()
        val hold = hold(booking, now.plusSeconds(3 * 3_600), SlotHoldStatus.CONFIRMED, JobStatus.ASSIGNED)
        stubAssigned(booking, hold)

        val result = feed.assignedJobs(workerId).single()

        assertNull(result.serviceLatitude)
        assertNull(result.serviceLongitude)
        assertNull(result.serviceAddressLine)
    }

    @Test
    fun `assigned job reveals exact location inside two hour window`() {
        val booking = booking()
        val hold = hold(booking, now.plusSeconds(90 * 60), SlotHoldStatus.CONFIRMED, JobStatus.ASSIGNED)
        stubAssigned(booking, hold)

        val result = feed.assignedJobs(workerId).single()

        assertEquals(booking.serviceLatitude, result.serviceLatitude)
        assertEquals(booking.serviceLongitude, result.serviceLongitude)
        assertEquals(booking.serviceAddressLine, result.serviceAddressLine)
    }

    private fun stubAssigned(booking: Booking, hold: SlotHold) {
        every {
            holds.findByWorkerIdAndStatusAndJobStatusInAndStartsAtAfterOrderByStartsAtAsc(
                workerId,
                SlotHoldStatus.CONFIRMED,
                any(),
                now.minusSeconds(12 * 3_600),
                any<Pageable>(),
            )
        } returns listOf(hold)
        every { bookings.findAllById(any<Iterable<UUID>>()) } returns listOf(booking)
        every { services.findAllById(any<Iterable<UUID>>()) } returns listOf(serviceOffering)
    }

    private fun booking() = Booking(
        UUID.randomUUID(),
        UUID.randomUUID(),
        serviceOffering.id,
        BookingType.SCHEDULED,
        BookingStatus.CONFIRMED,
        60,
        20_000,
        BigDecimal("19.0760"),
        BigDecimal("72.8777"),
        BookingRequirements(hasPets = true, preferredLanguages = listOf("Hindi")),
        null,
        now,
        now,
        serviceAddressId = UUID.randomUUID(),
        serviceAddressLabel = "Home",
        serviceAddressLine = "10 Test Street",
        serviceAddressPincode = "400001",
    )

    private fun hold(
        booking: Booking,
        startsAt: Instant,
        status: SlotHoldStatus,
        jobStatus: JobStatus?,
    ) = SlotHold(
        UUID.randomUUID(),
        booking.id,
        0,
        booking.customerId,
        workerId,
        serviceOffering.id,
        startsAt,
        startsAt.plusSeconds(3_600),
        status,
        now.plusSeconds(300),
        now,
        jobStatus,
    )
}
