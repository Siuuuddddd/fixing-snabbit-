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
