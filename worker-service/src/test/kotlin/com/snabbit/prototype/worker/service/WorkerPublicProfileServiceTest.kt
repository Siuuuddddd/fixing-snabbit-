package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import com.snabbit.prototype.worker.rating.WorkerRankingPolicy
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class WorkerPublicProfileServiceTest {
    @Test
    fun `public profile contains safe fields and no phone property`() {
        val worker = WorkerProfile(
            UUID.randomUUID(), "+919900000001", "Savita Private", WorkerTier.EXPERT,
            WorkerStatus.ACTIVE, BigDecimal("19.076"), BigDecimal("72.877"), BigDecimal("15"),
            490, 100, Instant.parse("2025-10-13T00:00:00Z"), Instant.parse("2026-10-13T00:00:00Z"),
        )
        val workers = mockk<WorkerProfileRepository> { every { findById(worker.id) } returns Optional.of(worker) }
        val ratings = mockk<WorkerRatingRepository> {
            every { findFirstByCustomerIdAndWorkerIdOrderByUpdatedAtDesc(any(), worker.id) } returns null
        }
        val tiers = mockk<WorkerTierPolicy> {
            every { currentTier(worker) } returns WorkerTier.EXPERT
            every { completedMonths(worker) } returns 12
        }
        val bookings = mockk<BookingCompletionClient> { every { completedCount(worker.id) } returns 88 }
        val ranking = mockk<WorkerRankingPolicy> { every { score(worker) } returns BigDecimal("4.83") }
        val service = WorkerPublicProfileService(workers, ratings, tiers, bookings, ranking)

        val snapshot = service.profile(worker.id, UUID.randomUUID())!!

        assertEquals("Savita", snapshot.firstName)
        assertEquals("EXPERT", snapshot.tier)
        assertEquals(88, snapshot.completedJobs)
        assertFalse(snapshot::class.members.any { it.name.contains("phone", ignoreCase = true) })
    }
}
