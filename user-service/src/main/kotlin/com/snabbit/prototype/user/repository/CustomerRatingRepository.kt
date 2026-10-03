package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.CustomerRating
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRatingRepository : JpaRepository<CustomerRating, UUID> {
    fun findByBookingIdAndVisitIndex(bookingId: UUID, visitIndex: Int): CustomerRating?
}
