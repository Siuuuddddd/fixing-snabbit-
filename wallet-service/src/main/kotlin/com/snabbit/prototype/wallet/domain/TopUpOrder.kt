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

enum class TopUpStatus { CREATING_ORDER, PENDING, PAID, FAILED }

@Entity
@Table(name = "top_up_order")
class TopUpOrder(
    @Id
    val id: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Column(name = "amount_minor", nullable = false)
    val amountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TopUpStatus,
    @Column(nullable = false)
    val provider: String,
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
    fun orderCreated(orderId: String, now: Instant) {
        check(status == TopUpStatus.CREATING_ORDER)
        providerOrderId = orderId
        status = TopUpStatus.PENDING
        updatedAt = now
    }

    fun paid(paymentId: String, now: Instant) {
        check(status == TopUpStatus.PENDING)
        providerPaymentId = paymentId
        status = TopUpStatus.PAID
        updatedAt = now
    }

    fun fail(now: Instant) {
        if (status == TopUpStatus.CREATING_ORDER || status == TopUpStatus.PENDING) {
            status = TopUpStatus.FAILED
            updatedAt = now
        }
    }
}
