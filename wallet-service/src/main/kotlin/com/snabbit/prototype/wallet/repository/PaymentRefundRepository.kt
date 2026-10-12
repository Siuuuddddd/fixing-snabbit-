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
    fun findFirstByBookingPaymentIdOrderByCreatedAtDesc(bookingPaymentId: UUID): PaymentRefund?
    fun findByBookingPaymentId(bookingPaymentId: UUID): PaymentRefund? =
        findFirstByBookingPaymentIdOrderByCreatedAtDesc(bookingPaymentId)
    fun findAllByBookingPaymentId(bookingPaymentId: UUID): List<PaymentRefund>

    @Query("select coalesce(sum(r.walletAmountMinor + r.onlineAmountMinor), 0) from PaymentRefund r where r.bookingPaymentId = :paymentId")
    fun totalRequested(@Param("paymentId") paymentId: UUID): Long

    @Query(
        value = "select coalesce(sum(wallet_amount_minor + online_amount_minor), 0) from payment_refund where booking_payment_id = :paymentId and status = 'COMPLETED'",
        nativeQuery = true,
    )
    fun totalCompleted(@Param("paymentId") paymentId: UUID): Long

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
