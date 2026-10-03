package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkerDirectoryServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val directory = WorkerDirectoryService(workers)

    @Test
    fun `contact returns phone owned by worker service`() {
        val worker = worker()
        every { workers.findById(worker.id) } returns Optional.of(worker)

        assertEquals(WorkerContactSnapshot(worker.id, worker.phoneE164), directory.contact(worker.id))
    }

    @Test
    fun `unknown worker has no contact`() {
        val workerId = UUID.randomUUID()
        every { workers.findById(workerId) } returns Optional.empty()

        assertNull(directory.contact(workerId))
    }

    private fun worker() = WorkerProfile(
        UUID.randomUUID(),
        "+919900000001",
        "Worker",
        WorkerTier.STANDARD,
        WorkerStatus.ACTIVE,
        BigDecimal("19.0760"),
        BigDecimal("72.8777"),
        BigDecimal("15"),
        0,
        0,
        Instant.parse("2026-10-02T04:00:00Z"),
        Instant.parse("2026-10-02T04:00:00Z"),
    )
}
