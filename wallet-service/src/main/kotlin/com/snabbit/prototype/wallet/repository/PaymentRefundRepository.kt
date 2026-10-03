package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.enums.RefundStatus
import com.snabbit.prototype.wallet.domain.PaymentRefund
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentRefundRepository : JpaRepository<PaymentRefund, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): PaymentRefund?
    fun findByBookingPaymentId(bookingPaymentId: UUID): PaymentRefund?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefund refund where refund.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PaymentRefund?

    fun findTop100ByStatusOrderByCreatedAt(status: RefundStatus): List<PaymentRefund>
    @Query(
        """
        select refund from PaymentRefund refund
        where refund.status = :readyStatus
           or (refund.status = :processingStatus and refund.updatedAt <= :staleBefore)
        order by refund.createdAt
        """,
    )
    fun findRetryable(
        @Param("readyStatus") readyStatus: RefundStatus,
        @Param("processingStatus") processingStatus: RefundStatus,
        @Param("staleBefore") staleBefore: Instant,
        pageable: Pageable,
    ): List<PaymentRefund>
    fun findTop100ByOrderByUpdatedAtDesc(): List<PaymentRefund>
    fun findTop100ByStatusOrderByUpdatedAtDesc(status: RefundStatus): List<PaymentRefund>
}
