package com.snabbit.prototype.wallet.domain

import com.snabbit.prototype.wallet.domain.enums.ReservationStatus

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "wallet_reservation")
class WalletReservation(
    @Id
    val id: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Column(name = "booking_id", nullable = false, unique = true)
    val bookingId: UUID,
    @Column(name = "amount_minor", nullable = false)
    val amountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReservationStatus,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun capture(now: Instant) {
        check(status == ReservationStatus.ACTIVE) { "Reservation is not active" }
        check(expiresAt.isAfter(now)) { "Reservation has expired" }
        status = ReservationStatus.CAPTURED
        updatedAt = now
    }

    fun release(now: Instant) {
        if (status == ReservationStatus.ACTIVE) {
            status = ReservationStatus.RELEASED
            updatedAt = now
        }
    }

    fun expire(now: Instant) {
        if (status == ReservationStatus.ACTIVE) {
            status = ReservationStatus.EXPIRED
            updatedAt = now
        }
    }
}
