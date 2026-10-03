package com.snabbit.prototype.user.domain

import com.snabbit.prototype.user.domain.enums.PlusPassStatus
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
@Table(name = "plus_pass")
class PlusPass(
    @Id val id: UUID,
    @Column(name = "subscription_id", nullable = false) val subscriptionId: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "service_code") val serviceCode: String?,
    @Column(name = "discount_minor", nullable = false) val discountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) var status: PlusPassStatus,
    @Column(name = "held_for_booking_id", unique = true) var heldForBookingId: UUID?,
    @Column(name = "hold_expires_at") var holdExpiresAt: Instant?,
    @Column(name = "used_at") var usedAt: Instant?,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Version var version: Long = 0,
) {
    fun reserve(bookingId: UUID, until: Instant, now: Instant) {
        check(status == PlusPassStatus.AVAILABLE) { "Pass is not available" }
        require(expiresAt.isAfter(until) || expiresAt == until) { "Pass expires before the booking hold" }
        require(until.isAfter(now)) { "Pass hold must expire in the future" }
        status = PlusPassStatus.HELD
        heldForBookingId = bookingId
        holdExpiresAt = until
    }

    fun consume(confirmedAt: Instant) {
        check(status == PlusPassStatus.HELD) { "Only a held pass can be consumed" }
        val reservedUntil = checkNotNull(holdExpiresAt) { "Held pass has no expiry" }
        require(!confirmedAt.isAfter(reservedUntil)) { "Booking was confirmed after the pass hold expired" }
        status = PlusPassStatus.USED
        holdExpiresAt = null
        usedAt = confirmedAt
    }

    fun reconcileConfirmed(confirmedAt: Instant) {
        check(status == PlusPassStatus.HELD) { "Only a held pass can be reconciled" }
        status = PlusPassStatus.USED
        holdExpiresAt = null
        usedAt = confirmedAt
    }

    fun release() {
        if (status == PlusPassStatus.HELD) {
            status = PlusPassStatus.AVAILABLE
            heldForBookingId = null
            holdExpiresAt = null
        }
    }

    fun restoreAfterPlatformCancellation() {
        if (status == PlusPassStatus.HELD || status == PlusPassStatus.USED) {
            status = PlusPassStatus.AVAILABLE
            heldForBookingId = null
            holdExpiresAt = null
            usedAt = null
        }
    }
}
