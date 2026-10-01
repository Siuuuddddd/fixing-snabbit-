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

enum class WorkerOfferStatus {
    OFFERED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
}

@Entity
@Table(name = "worker_offer")
class WorkerOffer(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,
    @Column(name = "slot_hold_id", nullable = false)
    val slotHoldId: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "worker_offer_status")
    var status: WorkerOfferStatus,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "responded_at")
    var respondedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun accept(now: Instant) {
        check(status == WorkerOfferStatus.OFFERED) { "Worker offer is no longer open" }
        check(expiresAt.isAfter(now)) { "Worker offer has expired" }
        status = WorkerOfferStatus.ACCEPTED
        respondedAt = now
    }

    fun reject(now: Instant) {
        check(status == WorkerOfferStatus.OFFERED) { "Worker offer is no longer open" }
        status = WorkerOfferStatus.REJECTED
        respondedAt = now
    }

    fun expire(now: Instant) {
        if (status == WorkerOfferStatus.OFFERED) {
            status = WorkerOfferStatus.EXPIRED
            respondedAt = now
        }
    }
}
