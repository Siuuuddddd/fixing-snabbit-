package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.dto.response.CustomerRatingSnapshot
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.CustomerRatingRejected
import com.snabbit.prototype.user.repository.CustomerRatingRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerRatingTransaction(
    private val users: UserProfileRepository,
    private val ratings: CustomerRatingRepository,
    private val clock: Clock,
    private val outbox: UserOutboxWriter,
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
        val now = clock.instant()
        val rating = ratings.save(
            CustomerRating(UUID.randomUUID(), bookingId, visitIndex, customerId, workerId, stars, comment, now),
        )
        customer.ratingSum += stars
        customer.ratingCount += 1
        outbox.append(
            customerId,
            "CustomerRated",
            mapOf(
                "userId" to customerId, "bookingId" to bookingId, "visitIndex" to visitIndex,
                "workerId" to workerId, "stars" to stars,
            ),
            now,
        )
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
