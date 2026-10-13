package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerRankingPolicyTest {
    private val policy = WorkerRankingPolicy(WorkerRatingProperties())

    @Test
    fun `established 4 point 7 ranks above a single 5 star rating`() {
        val singleFive = worker(5, 1)
        val established = worker(470, 100)

        assertTrue(policy.score(established) > policy.score(singleFive))
    }

    private fun worker(sum: Int, count: Int) = WorkerProfile(
        UUID.randomUUID(), "seed-placeholder-${UUID.randomUUID()}", "Worker", WorkerTier.STANDARD,
        WorkerStatus.ACTIVE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("15"), sum, count,
        Instant.EPOCH, Instant.EPOCH,
    )
}
