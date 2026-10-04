package com.snabbit.prototype.mobile.domain.wallet

data class WalletBalance(
    val walletId: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val reservedMinor: Long,
    val availableMinor: Long,
    val totalSpentMinor: Long,
)

data class WalletTopUp(
    val topUpId: String,
    val walletId: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

enum class LedgerEntryState { PENDING, CONFIRMED, FAILED }

data class WalletLedgerEntry(
    val id: String,
    val amountMinor: Long,
    val state: LedgerEntryState,
    val occurredAt: String,
    val description: String,
)

interface WalletIdDataSource {
    fun walletId(): String?
}

interface WalletRepository {
    suspend fun balance(): WalletBalance?
    suspend fun createTopUp(amountMinor: Long, idempotencyKey: String): WalletTopUp
    suspend fun history(): List<WalletLedgerEntry>
    suspend fun recordPrototypeEntry(entry: WalletLedgerEntry)
}

interface WalletLedgerDataSource {
    suspend fun history(walletId: String): List<WalletLedgerEntry>
    suspend fun upsert(walletId: String, entry: WalletLedgerEntry)
}
