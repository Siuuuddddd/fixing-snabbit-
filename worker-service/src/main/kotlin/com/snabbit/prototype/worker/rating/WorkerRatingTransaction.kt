package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.dto.response.WorkerRatingSnapshot
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import com.snabbit.prototype.worker.exception.WorkerRatingRejected
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerRatingTransaction(
    private val workers: WorkerProfileRepository,
    private val ratings: WorkerRatingRepository,
    private val clock: Clock,
    private val outbox: WorkerOutboxWriter,
    private val properties: WorkerRatingProperties,
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
        if (stars !in 1..5) {
            throw WorkerRatingRejected("Rating must be from 1 to 5 stars", "RATING_INVALID", HttpStatus.UNPROCESSABLE_ENTITY)
        }
        val comment = rawComment?.trim()?.takeIf(String::isNotBlank)
        val worker = workers.findForUpdate(workerId) ?: throw WorkerRatingRejected("Worker does not exist")
        val now = clock.instant()
        ratings.findByBookingIdAndVisitIndex(bookingId, visitIndex)?.let { existing ->
            if (existing.workerId != workerId || existing.customerId != customerId) {
                throw WorkerRatingRejected("This booking visit was rated by a different customer or worker")
            }
            if (existing.stars == stars && existing.comment == comment) {
                return snapshot(existing, worker.averageRating())
            }
            if (now.isAfter(existing.createdAt.plus(properties.updateWindow))) {
                throw WorkerRatingRejected(
                    "The rating update window has expired",
                    "RATING_UPDATE_WINDOW_EXPIRED",
                )
            }
            worker.ratingSum += stars - existing.stars
            existing.stars = stars
            existing.comment = comment
            existing.updatedAt = now
            appendRatedEvent(existing, now)
            return snapshot(existing, worker.averageRating())
        }
        val rating = ratings.save(
            WorkerRating(UUID.randomUUID(), bookingId, visitIndex, workerId, customerId, stars, comment, now, now),
        )
        worker.ratingSum += stars
        worker.ratingCount += 1
        appendRatedEvent(rating, now)
        return snapshot(rating, worker.averageRating())
    }

    private fun appendRatedEvent(rating: WorkerRating, now: Instant) {
        outbox.append(
            rating.workerId,
            "WorkerRated",
            mapOf(
                "ratingId" to rating.id,
                "workerId" to rating.workerId,
                "bookingId" to rating.bookingId,
                "visitIndex" to rating.visitIndex,
                "customerId" to rating.customerId,
            ),
            now,
        )
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
