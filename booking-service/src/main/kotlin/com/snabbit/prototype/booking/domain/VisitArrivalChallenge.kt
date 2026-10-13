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
    fun beginSend(now: Instant, maxSends: Int) {
        check(status == ArrivalChallengeStatus.PENDING || status == ArrivalChallengeStatus.DELIVERY_FAILED) {
            "Arrival OTP is not awaiting delivery"
        }
        check(sendCount < maxSends) { "Arrival OTP send limit reached" }
        sendCount += 1
        updatedAt = now
    }

    fun sent(providerReference: String, now: Instant, expiresAt: Instant) {
        require(providerReference.isNotBlank())
        providerVerificationRef = providerReference
        status = ArrivalChallengeStatus.SENT
        lastSentAt = now
        this.expiresAt = expiresAt
        updatedAt = now
    }

    fun deliveryFailed(now: Instant) {
        status = ArrivalChallengeStatus.DELIVERY_FAILED
        updatedAt = now
    }

    fun blockDelivery(now: Instant, maxSends: Int) {
        sendCount = maxSends
        deliveryFailed(now)
    }

    fun requestResend(now: Instant, cooldown: java.time.Duration, maxSends: Int) {
        check(status == ArrivalChallengeStatus.SENT || status == ArrivalChallengeStatus.DELIVERY_FAILED) {
            "Arrival OTP cannot be resent in its current state"
        }
        check(sendCount < maxSends) { "Arrival OTP send limit reached" }
        val allowedAt = lastSentAt?.plus(cooldown)
        check(allowedAt == null || !now.isBefore(allowedAt)) { "Arrival OTP resend cooldown is active" }
        status = ArrivalChallengeStatus.PENDING
        providerVerificationRef = null
        updatedAt = now
    }

    fun cancel(now: Instant) {
        if (status != ArrivalChallengeStatus.CANCELLED) {
            status = ArrivalChallengeStatus.CANCELLED
            updatedAt = now
        }
    }
}
