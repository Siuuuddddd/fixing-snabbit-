package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.BookingPayment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BookingPaymentRepository : JpaRepository<BookingPayment, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): BookingPayment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BookingPayment payment where payment.bookingId = :bookingId")
    fun findByBookingIdForUpdate(@Param("bookingId") bookingId: UUID): BookingPayment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BookingPayment payment where payment.id = :id")
    fun findForUpdate(@Param("id") id: UUID): BookingPayment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BookingPayment payment where payment.providerOrderId = :providerOrderId")
    fun findByProviderOrderIdForUpdate(@Param("providerOrderId") providerOrderId: String): BookingPayment?
}
