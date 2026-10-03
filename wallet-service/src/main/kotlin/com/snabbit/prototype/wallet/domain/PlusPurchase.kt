package com.snabbit.prototype.wallet.domain

import com.snabbit.prototype.wallet.domain.enums.PlusPurchaseStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "plus_purchase")
class PlusPurchase(
    @Id
    val id: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(name = "plan_code", nullable = false)
    val planCode: String,
    @Column(name = "amount_minor", nullable = false)
    val amountMinor: Long,
    @Column(name = "duration_days", nullable = false)
    val durationDays: Int,
    @Column(name = "pass_count", nullable = false)
    val passCount: Int,
    @Column(name = "pass_discount_minor", nullable = false)
    val passDiscountMinor: Long,
    @Column(name = "service_code")
    val serviceCode: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PlusPurchaseStatus,
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
        check(status == PlusPurchaseStatus.CREATING_ORDER)
        providerOrderId = orderId
        status = PlusPurchaseStatus.PENDING
        updatedAt = now
    }

    fun paid(paymentId: String, now: Instant) {
        check(status == PlusPurchaseStatus.PENDING)
        providerPaymentId = paymentId
        status = PlusPurchaseStatus.PAID
        updatedAt = now
    }

    fun fail(now: Instant) {
        if (status == PlusPurchaseStatus.CREATING_ORDER || status == PlusPurchaseStatus.PENDING) {
            status = PlusPurchaseStatus.FAILED
            updatedAt = now
        }
    }
}
