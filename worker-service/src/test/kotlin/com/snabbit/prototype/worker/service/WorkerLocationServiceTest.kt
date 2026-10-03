package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.dto.request.WorkerLocationCommand

import com.snabbit.prototype.worker.domain.enums.WorkerTier

import com.snabbit.prototype.worker.domain.enums.WorkerStatus

import com.snabbit.prototype.worker.domain.WorkerLocationPoint
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.repository.WorkerLocationRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import io.mockk.every
import io.mockk.mockk
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WorkerLocationServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val locations = mockk<WorkerLocationRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = WorkerLocationService(
        workers, locations, Clock.fixed(now, ZoneOffset.UTC), mockk<WorkerOutboxWriter>(relaxed = true),
    )

    @Test
    fun `accurate fresh point updates current location and appends history`() {
        val worker = worker()
        val saved = slot<WorkerLocationPoint>()
        every { workers.findForUpdate(worker.id) } returns worker
        every { locations.save(capture(saved)) } answers { firstArg() }

        val result = service.update(
            worker.id,
            WorkerLocationCommand(
                BigDecimal("19.076100"),
                BigDecimal("72.877400"),
                BigDecimal("12.5"),
                now.minusSeconds(5),
            ),
        )

        assertEquals(BigDecimal("19.076100"), worker.currentLatitude)
        assertEquals(BigDecimal("72.877400"), result.longitude)
        assertEquals(saved.captured.id, result.locationId)
        assertEquals(now, result.receivedAt)
        assertEquals(worker.id, saved.captured.workerId)
        assertEquals(now, saved.captured.receivedAt)
    }

    @Test
    fun `latest location returns the newest durable history point`() {
        val point = WorkerLocationPoint(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal("19.076100"),
            BigDecimal("72.877400"),
            BigDecimal("12.5"),
            now.minusSeconds(5),
            now,
        )
        every { locations.findFirstByWorkerIdOrderByReceivedAtDesc(point.workerId) } returns point

        val result = service.latest(point.workerId)

        assertEquals(point.id, result?.locationId)
        assertEquals(point.capturedAt, result?.capturedAt)
    }

    @Test
    fun `stale location is rejected before database mutation`() {
        val workerId = UUID.randomUUID()

        assertThrows(InvalidWorkerLocation::class.java) {
            service.update(
                workerId,
                WorkerLocationCommand(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, now.minusSeconds(301)),
            )
        }

        verify(exactly = 0) { workers.findForUpdate(any()) }
        verify(exactly = 0) { locations.save(any()) }
    }

    private fun worker() = WorkerProfile(
        UUID.randomUUID(),
        "+919900000001",
        "Asha",
        WorkerTier.VETERAN,
        WorkerStatus.ACTIVE,
        BigDecimal("19.076090"),
        BigDecimal("72.877426"),
        BigDecimal("15"),
        47,
        10,
        now,
        now,
    )
}
