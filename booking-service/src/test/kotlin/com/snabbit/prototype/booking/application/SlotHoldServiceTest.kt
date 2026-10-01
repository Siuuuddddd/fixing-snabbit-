package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.WorkerDayCapacity
import com.snabbit.prototype.booking.persistence.SlotHoldRepository
import com.snabbit.prototype.booking.persistence.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.support.SlotUnavailable
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class SlotHoldServiceTest {
    private val capacityRepository = mockk<WorkerDayCapacityRepository>()
    private val holdRepository = mockk<SlotHoldRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-02T04:00:00Z"), ZoneOffset.UTC)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"),
        LocalTime.of(8, 0),
        LocalTime.of(19, 0),
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
    )
    private val service = SlotHoldService(
        BookingWindowPolicy(properties, clock),
        capacityRepository,
        holdRepository,
        properties,
        clock,
    )

    @Test
    fun `creates a five minute hold when capacity is free`() {
        val command = command()
        every { capacityRepository.findForUpdate(command.workerId, LocalDate.parse("2026-10-02")) } returns capacity(command.workerId)
        every { holdRepository.hasActiveOverlap(command.workerId, command.startsAt, any(), clock.instant()) } returns false
        every { holdRepository.save(any()) } answers { firstArg() }

        val hold = service.hold(command)

        assertEquals(clock.instant().plus(Duration.ofMinutes(5)), hold.expiresAt)
        assertEquals(command.startsAt.plus(Duration.ofHours(2)), hold.endsAt)
        verify(exactly = 1) { capacityRepository.findForUpdate(command.workerId, LocalDate.parse("2026-10-02")) }
    }

    @Test
    fun `rejects an overlapping active hold`() {
        val command = command()
        every { capacityRepository.findForUpdate(command.workerId, LocalDate.parse("2026-10-02")) } returns capacity(command.workerId)
        every { holdRepository.hasActiveOverlap(command.workerId, command.startsAt, any(), clock.instant()) } returns true

        assertThrows(SlotUnavailable::class.java) { service.hold(command) }
        verify(exactly = 0) { holdRepository.save(any()) }
    }

    private fun command() = HoldSlotCommand(
        bookingId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        workerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.INSTANT,
        startsAt = Instant.parse("2026-10-02T04:30:00Z"),
        duration = Duration.ofHours(2),
    )

    private fun capacity(workerId: UUID) = WorkerDayCapacity(
        id = UUID.randomUUID(),
        workerId = workerId,
        serviceDate = LocalDate.parse("2026-10-02"),
        availableFrom = LocalTime.of(8, 0),
        availableUntil = LocalTime.of(19, 0),
    )
}
