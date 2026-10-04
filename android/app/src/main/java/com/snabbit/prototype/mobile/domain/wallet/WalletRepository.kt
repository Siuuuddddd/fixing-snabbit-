package com.snabbit.prototype.mobile.domain.wallet

data class WalletBalance(
    val walletId: String,
    val currency: String,
    val availableMinor: Long,
)

interface WalletIdDataSource {
    fun walletId(): String?
}

interface WalletRepository {
    suspend fun balance(): WalletBalance?
}
