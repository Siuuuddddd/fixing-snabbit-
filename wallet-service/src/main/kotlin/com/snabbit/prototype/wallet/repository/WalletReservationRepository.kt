package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.WalletReservation
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WalletReservationRepository : JpaRepository<WalletReservation, UUID> {
    @Query(
        value = """
            SELECT COALESCE(SUM(amount_minor), 0)::bigint
            FROM wallet_reservation
            WHERE wallet_id = :walletId
              AND status = 'ACTIVE'
              AND expires_at > :now
        """,
        nativeQuery = true,
    )
    fun activeTotal(@Param("walletId") walletId: UUID, @Param("now") now: Instant): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from WalletReservation reservation where reservation.id = :id")
    fun findForUpdate(@Param("id") id: UUID): WalletReservation?

    fun findByBookingId(bookingId: UUID): WalletReservation?
}
