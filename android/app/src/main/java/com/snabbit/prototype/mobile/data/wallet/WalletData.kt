package com.snabbit.prototype.mobile.data.wallet

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletIdDataSource
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable data class WalletBalanceDto(
    val walletId: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val reservedMinor: Long,
    val availableMinor: Long,
    val totalSpentMinor: Long,
)

interface WalletApi {
    @GET("api/v1/wallets/{walletId}") suspend fun balance(@Path("walletId") walletId: String): WalletBalanceDto
}

/** Temporary bridge until the backend exposes the authenticated customer's wallet id. */
class PrototypeConfiguredWalletIdDataSource(private val configuredId: String) : WalletIdDataSource {
    override fun walletId(): String? = configuredId.trim().ifBlank { null }
}

class DefaultWalletRepository(
    private val api: WalletApi,
    private val walletIdSource: WalletIdDataSource,
    private val json: Json,
) : WalletRepository {
    override suspend fun balance(): WalletBalance? {
        val id = walletIdSource.walletId() ?: return null
        return apiCall(json) { api.balance(id).let { WalletBalance(it.walletId, it.currency, it.availableMinor) } }
    }
}
