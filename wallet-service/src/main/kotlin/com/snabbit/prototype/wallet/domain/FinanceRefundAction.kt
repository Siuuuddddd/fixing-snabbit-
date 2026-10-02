package com.snabbit.prototype.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class FinanceRefundActionStatus { REQUESTED, SUCCEEDED, FAILED }

@Entity
@Table(name = "finance_refund_action")
class FinanceRefundAction(
    @Id
    val id: UUID,
    @Column(name = "refund_id", nullable = false)
    val refundId: UUID,
    @Column(name = "staff_id", nullable = false)
    val staffId: UUID,
    @Column(nullable = false)
    val action: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FinanceRefundActionStatus,
    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    fun succeed(now: Instant) {
        status = FinanceRefundActionStatus.SUCCEEDED
        updatedAt = now
    }

    fun fail(reason: String, now: Instant) {
        status = FinanceRefundActionStatus.FAILED
        failureReason = reason.take(500)
        updatedAt = now
    }
}
