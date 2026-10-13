package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "visit_arrival_challenge")
class VisitArrivalChallenge(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,
    @Column(name = "visit_index", nullable = false)
    val visitIndex: Int,
    @Column(name = "slot_hold_id", nullable = false, unique = true)
    val slotHoldId: UUID,
    @Column(name = "assigned_worker_id", nullable = false)
    val assignedWorkerId: UUID,
    @Column(name = "destination_phone_e164", nullable = false, length = 13)
    val destinationPhoneE164: String,
    @Column(name = "provider_verification_ref", length = 160)
    var providerVerificationRef: String? = null,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "arrival_challenge_status")
    var status: ArrivalChallengeStatus,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "send_count", nullable = false)
    var sendCount: Int = 0,
    @Column(name = "last_sent_at")
    var lastSentAt: Instant? = null,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun cancel(now: Instant) {
        if (status != ArrivalChallengeStatus.CANCELLED) {
            status = ArrivalChallengeStatus.CANCELLED
            updatedAt = now
        }
    }
}
