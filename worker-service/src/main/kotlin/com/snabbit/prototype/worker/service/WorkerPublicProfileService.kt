package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.dto.response.PublicWorkerProfile
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import com.snabbit.prototype.worker.rating.WorkerRankingPolicy
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerPublicProfileService(
    private val workers: WorkerProfileRepository,
    private val ratings: WorkerRatingRepository,
    private val tiers: WorkerTierPolicy,
    private val bookings: BookingCompletionClient,
    private val ranking: WorkerRankingPolicy,
) {
    @Transactional(readOnly = true)
    fun profile(workerId: UUID, customerId: UUID? = null): PublicWorkerProfile? {
        val worker = workers.findById(workerId).orElse(null)?.takeIf { it.status == WorkerStatus.ACTIVE } ?: return null
        val customerRating = customerId?.let {
            ratings.findFirstByCustomerIdAndWorkerIdOrderByUpdatedAtDesc(it, workerId)?.stars
        }
        val average = worker.averageRating()
        return PublicWorkerProfile(
            workerId = worker.id,
            firstName = worker.displayName.trim().substringBefore(' '),
            tier = tiers.currentTier(worker).name,
            averageRating = average,
            ratingCount = worker.ratingCount,
            completedJobs = bookings.completedCount(worker.id),
            tenureMonths = tiers.completedMonths(worker),
            rankScore = ranking.score(worker),
            customerRating = customerRating,
        )
    }

    @Transactional(readOnly = true)
    fun profiles(workerIds: List<UUID>, customerId: UUID?): List<PublicWorkerProfile> =
        workerIds.distinct().mapNotNull { profile(it, customerId) }
}
