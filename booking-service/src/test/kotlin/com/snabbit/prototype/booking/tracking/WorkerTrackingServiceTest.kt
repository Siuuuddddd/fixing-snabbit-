package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.BookingSnapshot
import com.snabbit.prototype.booking.service.VisitSnapshot
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.support.CustomerAccessDenied
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WorkerTrackingServiceTest {
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val bookings = mockk<BookingLifecycleService>()
    private val locations = mockk<WorkerLocationClient>()
    private val service = WorkerTrackingService(
        bookings,
        locations,
        WorkerTrackingProperties(Duration.ofHours(2), Duration.ofMillis(1), Duration.ofMinutes(2)),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `owner receives fresh accurate location for a confirmed upcoming visit`() {
        val booking = booking()
        val location = location(booking.visits.single().workerId)
        every { bookings.get(booking.bookingId) } returns booking
        every { locations.latest(location.workerId) } returns location

        val result = service.latest(booking.bookingId, 0, booking.customerId)

        assertEquals(location.locationId, result.locationId)
        assertEquals(booking.bookingId, result.bookingId)
        assertEquals(BigDecimal("12.5"), result.accuracyMeters)
    }

    @Test
    fun `non-owner cannot learn worker location`() {
        val booking = booking()
        every { bookings.get(booking.bookingId) } returns booking

        assertThrows(CustomerAccessDenied::class.java) {
            service.latest(booking.bookingId, 0, UUID.randomUUID())
        }

        verify(exactly = 0) { locations.latest(any()) }
    }

    @Test
    fun `tracking stays closed before privacy window and after completion`() {
        val future = booking(startsAt = now.plus(Duration.ofHours(3)))
        every { bookings.get(future.bookingId) } returns future
        assertThrows(WorkerTrackingUnavailable::class.java) {
            service.latest(future.bookingId, 0, future.customerId)
        }

        val completed = booking(jobStatus = "COMPLETED")
        every { bookings.get(completed.bookingId) } returns completed
        assertThrows(WorkerTrackingUnavailable::class.java) {
            service.latest(completed.bookingId, 0, completed.customerId)
        }
        verify(exactly = 0) { locations.latest(any()) }
    }

    @Test
    fun `stale worker location is not disclosed`() {
        val booking = booking()
        every { bookings.get(booking.bookingId) } returns booking
        every { locations.latest(any()) } returns location(
            booking.visits.single().workerId,
            capturedAt = now.minus(Duration.ofMinutes(3)),
        )

        assertThrows(WorkerTrackingUnavailable::class.java) {
            service.latest(booking.bookingId, 0, booking.customerId)
        }
    }

    @Test
    fun `stream emits changed locations and keep-alive comments`() = runBlocking {
        val booking = booking()
        val location = location(booking.visits.single().workerId)
        every { bookings.get(booking.bookingId) } returns booking
        every { locations.latest(location.workerId) } returns location

        val events = service.stream(booking.bookingId, 0, booking.customerId).take(2).toList()

        assertEquals("worker-location", events[0].event())
        assertEquals(location.locationId.toString(), events[0].id())
        assertEquals(location.locationId, events[0].data()?.locationId)
        assertEquals("keep-alive", events[1].comment())
        assertNull(events[1].data())
    }

    private fun booking(
        startsAt: Instant = now.plus(Duration.ofHours(1)),
        jobStatus: String = "ASSIGNED",
    ): BookingSnapshot {
        val bookingId = UUID.randomUUID()
        return BookingSnapshot(
            bookingId,
            UUID.randomUUID(),
            "CONFIRMED",
            10000,
            10000,
            0,
            null,
            BookingRequirements(),
            listOf(
                VisitSnapshot(
                    0,
                    UUID.randomUUID(),
                    startsAt,
                    startsAt.plus(Duration.ofHours(2)),
                    now.plusSeconds(300),
                    UUID.randomUUID(),
                    "ACCEPTED",
                    now.plusSeconds(60),
                    jobStatus,
                    null,
                    null,
                    if (jobStatus == "COMPLETED") now else null,
                ),
            ),
        )
    }

    private fun location(workerId: UUID, capturedAt: Instant = now.minusSeconds(5)) = WorkerLocationResponse(
        UUID.randomUUID(),
        workerId,
        BigDecimal("19.076100"),
        BigDecimal("72.877400"),
        BigDecimal("12.5"),
        capturedAt,
        now,
    )
}
