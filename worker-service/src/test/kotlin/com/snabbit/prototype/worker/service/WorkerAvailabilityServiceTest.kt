package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.AvailabilityStatus
import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class WorkerAvailabilityServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val availability = mockk<WorkerAvailabilityRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = WorkerAvailabilityService(workers, availability, Clock.fixed(now, ZoneOffset.UTC))
    private val workerId = UUID.randomUUID()

    @Test
    fun `worker can create an available operating-hours window`() {
        val date = LocalDate.parse("2026-10-03")
        every { workers.findForUpdate(workerId) } returns mockk<WorkerProfile>()
        every { availability.findByWorkerIdAndServiceDate(workerId, date) } returns null
        every { availability.save(any()) } answers { firstArg() }

        val result = service.update(
            workerId,
            date,
            WorkerAvailabilityCommand(
                AvailabilityStatus.AVAILABLE,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
            ),
        )

        assertEquals(AvailabilityStatus.AVAILABLE, result.status)
        assertEquals(LocalTime.of(9, 0), result.availableFrom)
        assertEquals(LocalTime.of(17, 0), result.availableUntil)
    }

    @Test
    fun `unavailable day stores a canonical window without trusting request times`() {
        val date = LocalDate.parse("2026-10-03")
        val existing = row(date)
        every { workers.findForUpdate(workerId) } returns mockk<WorkerProfile>()
        every { availability.findByWorkerIdAndServiceDate(workerId, date) } returns existing
        every { availability.save(existing) } returns existing

        val result = service.update(
            workerId,
            date,
            WorkerAvailabilityCommand(AvailabilityStatus.UNAVAILABLE, null, null),
        )

        assertEquals(AvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals(LocalTime.of(8, 0), result.availableFrom)
        assertEquals(LocalTime.of(19, 0), result.availableUntil)
    }

    @Test
    fun `window outside operating hours is rejected before a row is written`() {
        val date = LocalDate.parse("2026-10-03")
        every { workers.findForUpdate(workerId) } returns mockk<WorkerProfile>()

        assertThrows(InvalidWorkerAvailability::class.java) {
            service.update(
                workerId,
                date,
                WorkerAvailabilityCommand(
                    AvailabilityStatus.AVAILABLE,
                    LocalTime.of(7, 0),
                    LocalTime.of(12, 0),
                ),
            )
        }

        verify(exactly = 0) { availability.save(any()) }
    }

    @Test
    fun `listing is limited to a bounded future range`() {
        val from = LocalDate.parse("2026-10-02")
        val to = LocalDate.parse("2026-11-02")

        assertThrows(InvalidWorkerAvailability::class.java) {
            service.list(workerId, from, to)
        }

        verify(exactly = 0) { workers.existsById(any()) }
    }

    private fun row(date: LocalDate) = WorkerAvailability(
        id = UUID.randomUUID(),
        workerId = workerId,
        serviceDate = date,
        availableFrom = LocalTime.of(9, 0),
        availableUntil = LocalTime.of(17, 0),
        status = AvailabilityStatus.AVAILABLE,
    )
}
