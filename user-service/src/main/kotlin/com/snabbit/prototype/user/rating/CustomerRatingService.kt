package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.dto.response.CustomerRatingSnapshot
import com.snabbit.prototype.user.exception.CustomerRatingRejected
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class CustomerRatingService(
    private val bookings: CustomerRatingBookingClient,
    private val transactions: CustomerRatingTransaction,
) {
    fun rate(
        authenticatedWorkerId: UUID,
        customerId: UUID,
        bookingId: UUID,
        visitIndex: Int,
        stars: Int,
        comment: String?,
    ): CustomerRatingSnapshot {
        val context = bookings.context(bookingId)
        if (context.customerId != customerId) throw CustomerRatingRejected("Booking belongs to a different customer")
        val visit = context.visits.singleOrNull { it.visitIndex == visitIndex }
            ?: throw CustomerRatingRejected("Booking visit does not exist")
        if (visit.workerId != authenticatedWorkerId || !visit.completed) {
            throw CustomerRatingRejected("Only the completed visit's assigned worker can rate the customer")
        }
        return transactions.record(bookingId, visitIndex, customerId, authenticatedWorkerId, stars, comment)
    }
}
