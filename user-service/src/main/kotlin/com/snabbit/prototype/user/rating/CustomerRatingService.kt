package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.persistence.CustomerRatingRepository
import com.snabbit.prototype.user.persistence.UserProfileRepository
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

@ConfigurationProperties("integrations")
data class UserIntegrationProperties(val bookingServiceUrl: String)

data class BookingRatingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<BookingRatingVisit>,
)

data class BookingRatingVisit(val visitIndex: Int, val workerId: UUID, val completed: Boolean)

data class CustomerRatingSnapshot(
    val ratingId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val customerId: UUID,
    val workerId: UUID,
    val stars: Int,
    val comment: String?,
    val customerAverageRating: BigDecimal,
)

class CustomerRatingRejected(message: String) : RuntimeException(message)

@Component
class CustomerRatingBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): BookingRatingContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/rating-context", bookingId)
        .retrieve()
        .body(BookingRatingContext::class.java)
        ?: throw CustomerRatingRejected("Booking Service returned an empty rating context")
}

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

@Service
class CustomerRatingTransaction(
    private val users: UserProfileRepository,
    private val ratings: CustomerRatingRepository,
    private val clock: Clock,
) {
    @Transactional
    fun record(
        bookingId: UUID,
        visitIndex: Int,
        customerId: UUID,
        workerId: UUID,
        stars: Int,
        rawComment: String?,
    ): CustomerRatingSnapshot {
        if (stars !in 1..5) throw CustomerRatingRejected("Rating must be from 1 to 5 stars")
        val comment = rawComment?.trim()?.takeIf(String::isNotBlank)
        val customer = users.findForUpdate(customerId) ?: throw CustomerRatingRejected("Customer does not exist")
        ratings.findByBookingIdAndVisitIndex(bookingId, visitIndex)?.let { existing ->
            if (existing.customerId != customerId || existing.workerId != workerId ||
                existing.stars != stars || existing.comment != comment
            ) {
                throw CustomerRatingRejected("This booking visit has already been rated differently")
            }
            return snapshot(existing, customer.averageRating())
        }
        val rating = ratings.save(
            CustomerRating(UUID.randomUUID(), bookingId, visitIndex, customerId, workerId, stars, comment, clock.instant()),
        )
        customer.ratingSum += stars
        customer.ratingCount += 1
        return snapshot(rating, customer.averageRating())
    }

    private fun snapshot(rating: CustomerRating, average: BigDecimal) = CustomerRatingSnapshot(
        rating.id,
        rating.bookingId,
        rating.visitIndex,
        rating.customerId,
        rating.workerId,
        rating.stars,
        rating.comment,
        average,
    )
}
