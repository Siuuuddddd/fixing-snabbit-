package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.PaymentRefund
import com.snabbit.prototype.wallet.domain.RefundStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PaymentRefundRepository : JpaRepository<PaymentRefund, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): PaymentRefund?
    fun findByBookingPaymentId(bookingPaymentId: UUID): PaymentRefund?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefund refund where refund.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PaymentRefund?

    fun findTop100ByStatusOrderByCreatedAt(status: RefundStatus): List<PaymentRefund>
    fun findTop100ByOrderByUpdatedAtDesc(): List<PaymentRefund>
    fun findTop100ByStatusOrderByUpdatedAtDesc(status: RefundStatus): List<PaymentRefund>
}
