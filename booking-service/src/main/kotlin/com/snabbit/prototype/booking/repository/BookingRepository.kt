package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.Booking
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID> {
    fun findByCustomerIdAndIdempotencyKey(customerId: UUID, idempotencyKey: String): Booking?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Booking?
}
