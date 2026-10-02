package com.snabbit.prototype.wallet.persistence

import com.snabbit.prototype.wallet.domain.WalletLedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WalletLedgerRepository : JpaRepository<WalletLedgerEntry, UUID> {
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean

    @Query(
        value = """
            SELECT COALESCE(SUM(
                CASE WHEN entry_type = 'CREDIT' THEN amount_minor ELSE -amount_minor END
            ), 0)::bigint
            FROM wallet_ledger_entry
            WHERE wallet_id = :walletId
        """,
        nativeQuery = true,
    )
    fun netBalance(@Param("walletId") walletId: UUID): Long

    @Query(
        value = """
            SELECT COALESCE(SUM(
                payment.total_amount_minor -
                CASE
                    WHEN refund.status = 'COMPLETED'
                    THEN refund.wallet_amount_minor + refund.online_amount_minor
                    ELSE 0
                END
            ), 0)::bigint
            FROM booking_payment payment
            LEFT JOIN payment_refund refund ON refund.booking_payment_id = payment.id
            WHERE payment.wallet_id = :walletId
              AND payment.status IN ('COMPLETED', 'REFUND_PENDING', 'REFUNDED')
        """,
        nativeQuery = true,
    )
    fun totalSpent(@Param("walletId") walletId: UUID): Long
}
