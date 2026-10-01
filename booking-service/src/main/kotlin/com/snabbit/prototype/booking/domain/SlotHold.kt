package com.snabbit.prototype.booking.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class SlotHoldStatus {
    HELD,
    CONFIRMED,
    EXPIRED,
    RELEASED,
}

@Entity
@Table(name = "slot_hold")
class SlotHold(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,
    @Column(name = "visit_index", nullable = false)
    val visitIndex: Int,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(name = "service_id", nullable = false)
    val serviceId: UUID,
    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,
    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "slot_hold_status")
    var status: SlotHoldStatus,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun confirm() {
        check(status == SlotHoldStatus.HELD) { "Only held slots can be confirmed" }
        status = SlotHoldStatus.CONFIRMED
    }

    fun expire() {
        if (status == SlotHoldStatus.HELD) status = SlotHoldStatus.EXPIRED
    }

    fun release() {
        if (status == SlotHoldStatus.HELD) status = SlotHoldStatus.RELEASED
    }

    fun isActive(now: Instant): Boolean =
        status == SlotHoldStatus.CONFIRMED || (status == SlotHoldStatus.HELD && expiresAt.isAfter(now))
}
