package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.dto.response.WorkerRatingSnapshot

import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import com.snabbit.prototype.worker.security.InternalServiceClientFactory
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class WorkerRatingTransaction(
    private val workers: WorkerProfileRepository,
    private val ratings: WorkerRatingRepository,
    private val clock: Clock,
    private val outbox: WorkerOutboxWriter,
) {
    @Transactional
    fun record(
        bookingId: UUID,
        visitIndex: Int,
        workerId: UUID,
        customerId: UUID,
        stars: Int,
        rawComment: String?,
    ): WorkerRatingSnapshot {
        if (stars !in 1..5) throw WorkerRatingRejected("Rating must be from 1 to 5 stars")
        val comment = rawComment?.trim()?.takeIf(String::isNotBlank)
        val worker = workers.findForUpdate(workerId) ?: throw WorkerRatingRejected("Worker does not exist")
        ratings.findByBookingIdAndVisitIndex(bookingId, visitIndex)?.let { existing ->
            if (existing.workerId != workerId || existing.customerId != customerId ||
                existing.stars != stars || existing.comment != comment
            ) {
                throw WorkerRatingRejected("This booking visit has already been rated differently")
            }
            return snapshot(existing, worker.averageRating())
        }
        val now = clock.instant()
        val rating = ratings.save(
            WorkerRating(UUID.randomUUID(), bookingId, visitIndex, workerId, customerId, stars, comment, now),
        )
        worker.ratingSum += stars
        worker.ratingCount += 1
        outbox.append(
            workerId,
            "WorkerRated",
            mapOf(
                "workerId" to workerId, "bookingId" to bookingId, "visitIndex" to visitIndex,
                "customerId" to customerId, "stars" to stars,
            ),
            now,
        )
        return snapshot(rating, worker.averageRating())
    }

    private fun snapshot(rating: WorkerRating, average: BigDecimal) = WorkerRatingSnapshot(
        rating.id,
        rating.bookingId,
        rating.visitIndex,
        rating.workerId,
        rating.customerId,
        rating.stars,
        rating.comment,
        average,
    )
}
