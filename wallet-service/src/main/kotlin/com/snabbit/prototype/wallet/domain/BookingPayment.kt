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

enum class BookingPaymentStatus {
    CREATING_ORDER,
    PENDING,
    COMPLETED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
}

@Entity
@Table(name = "booking_payment")
class BookingPayment(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false, unique = true)
    val bookingId: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Column(name = "reservation_id")
    val reservationId: UUID?,
    @Column(name = "total_amount_minor", nullable = false)
    val totalAmountMinor: Long,
    @Column(name = "wallet_amount_minor", nullable = false)
    val walletAmountMinor: Long,
    @Column(name = "online_amount_minor", nullable = false)
    val onlineAmountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookingPaymentStatus,
    @Column
    var provider: String? = null,
    @Column(name = "provider_order_id", unique = true)
    var providerOrderId: String? = null,
    @Column(name = "provider_payment_id")
    var providerPaymentId: String? = null,
    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun providerOrderCreated(orderId: String, now: Instant) {
        check(status == BookingPaymentStatus.CREATING_ORDER)
        provider = "RAZORPAY"
        providerOrderId = orderId
        status = BookingPaymentStatus.PENDING
        updatedAt = now
    }

    fun complete(paymentId: String?, now: Instant) {
        check(status == BookingPaymentStatus.PENDING || status == BookingPaymentStatus.CREATING_ORDER)
        providerPaymentId = paymentId
        status = BookingPaymentStatus.COMPLETED
        updatedAt = now
    }

    fun fail(now: Instant) {
        if (status == BookingPaymentStatus.CREATING_ORDER || status == BookingPaymentStatus.PENDING) {
            status = BookingPaymentStatus.FAILED
            updatedAt = now
        }
    }

    fun beginRefund(now: Instant) {
        check(status == BookingPaymentStatus.COMPLETED)
        status = BookingPaymentStatus.REFUND_PENDING
        updatedAt = now
    }

    fun beginCompensationRefund(paymentId: String, now: Instant) {
        check(status == BookingPaymentStatus.PENDING || status == BookingPaymentStatus.FAILED)
        providerPaymentId = paymentId
        status = BookingPaymentStatus.REFUND_PENDING
        updatedAt = now
    }

    fun refunded(now: Instant) {
        check(status == BookingPaymentStatus.REFUND_PENDING)
        status = BookingPaymentStatus.REFUNDED
        updatedAt = now
    }
}
