package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import com.snabbit.prototype.worker.security.InternalServiceClientFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

@ConfigurationProperties("integrations")
data class WorkerIntegrationProperties(val bookingServiceUrl: String)

data class BookingRatingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<BookingRatingVisit>,
)

data class BookingRatingVisit(
    val visitIndex: Int,
    val workerId: UUID,
    val completed: Boolean,
)

data class WorkerRatingSnapshot(
    val ratingId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val workerId: UUID,
    val customerId: UUID,
    val stars: Int,
    val comment: String?,
    val workerAverageRating: BigDecimal,
)

class WorkerRatingRejected(message: String) : RuntimeException(message)

@Component
class BookingRatingClient(factory: InternalServiceClientFactory, properties: WorkerIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): BookingRatingContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/rating-context", bookingId)
        .retrieve()
        .body(BookingRatingContext::class.java)
        ?: throw WorkerRatingRejected("Booking Service returned an empty rating context")
}

@Service
class WorkerRatingService(
    private val bookingClient: BookingRatingClient,
    private val transactions: WorkerRatingTransaction,
) {
    fun rate(
        authenticatedCustomerId: UUID,
        workerId: UUID,
        bookingId: UUID,
        visitIndex: Int,
        stars: Int,
        comment: String?,
    ): WorkerRatingSnapshot {
        val context = bookingClient.context(bookingId)
        if (context.customerId != authenticatedCustomerId) {
            throw WorkerRatingRejected("Customer does not own this booking")
        }
        val visit = context.visits.singleOrNull { it.visitIndex == visitIndex }
            ?: throw WorkerRatingRejected("Booking visit does not exist")
        if (visit.workerId != workerId || !visit.completed) {
            throw WorkerRatingRejected("Only the completed visit's assigned worker can be rated")
        }
        return transactions.record(bookingId, visitIndex, workerId, authenticatedCustomerId, stars, comment)
    }
}

@Service
class WorkerRatingTransaction(
    private val workers: WorkerProfileRepository,
    private val ratings: WorkerRatingRepository,
    private val clock: Clock,
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
        val rating = ratings.save(
            WorkerRating(UUID.randomUUID(), bookingId, visitIndex, workerId, customerId, stars, comment, clock.instant()),
        )
        worker.ratingSum += stars
        worker.ratingCount += 1
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
