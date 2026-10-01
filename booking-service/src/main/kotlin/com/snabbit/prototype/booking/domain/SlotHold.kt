package com.snabbit.prototype.booking.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
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
    @Column(nullable = false, columnDefinition = "slot_hold_status")
    var status: SlotHoldStatus,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Version
    var version: Long = 0,
)
