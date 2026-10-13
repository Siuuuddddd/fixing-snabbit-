package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.dto.response.WorkerRatingSnapshot
import com.snabbit.prototype.worker.exception.WorkerRatingRejected
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

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
            throw WorkerRatingRejected("Customer does not own this booking", "RATING_NOT_ALLOWED", HttpStatus.FORBIDDEN)
        }
        val visit = context.visits.singleOrNull { it.visitIndex == visitIndex }
            ?: throw WorkerRatingRejected("Booking visit does not exist", "RATING_NOT_ALLOWED", HttpStatus.FORBIDDEN)
        if (visit.workerId != workerId || !visit.completed) {
            throw WorkerRatingRejected(
                "Only the completed visit's assigned worker can be rated",
                "RATING_NOT_ALLOWED",
                HttpStatus.FORBIDDEN,
            )
        }
        return transactions.record(bookingId, visitIndex, workerId, authenticatedCustomerId, stars, comment)
    }
}
