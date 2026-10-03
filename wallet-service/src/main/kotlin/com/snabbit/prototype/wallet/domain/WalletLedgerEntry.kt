package com.snabbit.prototype.wallet.domain

import com.snabbit.prototype.wallet.domain.enums.LedgerEntryType

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "wallet_ledger_entry")
class WalletLedgerEntry(
    @Id
    val id: UUID,
    @Column(name = "wallet_id", nullable = false)
    val walletId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    val entryType: LedgerEntryType,
    @Column(name = "amount_minor", nullable = false)
    val amountMinor: Long,
    @Column(name = "reference_type", nullable = false)
    val referenceType: String,
    @Column(name = "reference_id", nullable = false)
    val referenceId: String,
    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,
    @Column(nullable = false)
    val description: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
)
