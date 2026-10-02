package com.snabbit.prototype.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class RefundStatus { CREATING, PROCESSING, COMPLETED, FAILED }

@Entity
@Table(name = "payment_refund")
class PaymentRefund(
    @Id
    val id: UUID,
    @Column(name = "booking_payment_id", nullable = false, unique = true)
    val bookingPaymentId: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Column(name = "wallet_amount_minor", nullable = false)
    val walletAmountMinor: Long,
    @Column(name = "online_amount_minor", nullable = false)
    val onlineAmountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus,
    @Column(name = "provider_refund_id", unique = true)
    var providerRefundId: String? = null,
    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun beginProcessing(now: Instant) {
        check(status == RefundStatus.CREATING || status == RefundStatus.PROCESSING)
        status = RefundStatus.PROCESSING
        updatedAt = now
    }

    fun complete(providerRefundId: String?, now: Instant) {
        check(status == RefundStatus.PROCESSING)
        this.providerRefundId = providerRefundId
        status = RefundStatus.COMPLETED
        updatedAt = now
    }

    fun retryLater(now: Instant) {
        if (status == RefundStatus.PROCESSING) {
            status = RefundStatus.CREATING
            updatedAt = now
        }
    }
}
