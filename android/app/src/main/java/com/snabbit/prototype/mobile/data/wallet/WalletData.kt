package com.snabbit.prototype.mobile.data.wallet

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletIdDataSource
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerDataSource
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerEntry
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletTopUp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable data class WalletBalanceDto(
    val walletId: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val reservedMinor: Long,
    val availableMinor: Long,
    val totalSpentMinor: Long,
)

@Serializable data class AddMoneyDto(val amountMinor: Long)

@Serializable data class TopUpDto(
    val topUpId: String,
    val walletId: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String? = null,
)

interface WalletApi {
    @GET("api/v1/wallets/{walletId}") suspend fun balance(@Path("walletId") walletId: String): WalletBalanceDto
    @POST("api/v1/wallets/{walletId}/top-ups")
    suspend fun createTopUp(
        @Path("walletId") walletId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: AddMoneyDto,
    ): TopUpDto
}

/** Temporary bridge until the backend exposes the authenticated customer's wallet id. */
class PrototypeConfiguredWalletIdDataSource(private val configuredId: String) : WalletIdDataSource {
    override fun walletId(): String? = configuredId.trim().ifBlank { null }
}

/** Process-local prototype history until the backend exposes a customer ledger endpoint. */
class PrototypeWalletLedgerDataSource : WalletLedgerDataSource {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, MutableList<WalletLedgerEntry>>()

    override suspend fun history(walletId: String): List<WalletLedgerEntry> = mutex.withLock {
        entries[walletId].orEmpty().sortedByDescending { it.occurredAt }
    }

    override suspend fun upsert(walletId: String, entry: WalletLedgerEntry) = mutex.withLock {
        val walletEntries = entries.getOrPut(walletId) { mutableListOf() }
        walletEntries.removeAll { it.id == entry.id }
        walletEntries += entry
    }
}

class DefaultWalletRepository(
    private val api: WalletApi,
    private val walletIdSource: WalletIdDataSource,
    private val ledger: WalletLedgerDataSource,
    private val json: Json,
) : WalletRepository {
    override suspend fun balance(): WalletBalance? {
        val id = walletIdSource.walletId() ?: return null
        return apiCall(json) {
            api.balance(id).let {
                WalletBalance(it.walletId, it.currency, it.ledgerBalanceMinor, it.reservedMinor, it.availableMinor, it.totalSpentMinor)
            }
        }
    }

    override suspend fun createTopUp(amountMinor: Long, idempotencyKey: String): WalletTopUp {
        val id = walletIdSource.walletId()
            ?: throw DataFailure(DataFailureReason.VALIDATION, "Configure PROTOTYPE_WALLET_ID to add money.")
        return apiCall(json) {
            api.createTopUp(id, idempotencyKey, AddMoneyDto(amountMinor)).let {
                WalletTopUp(it.topUpId, it.walletId, it.amountMinor, it.status, it.providerOrderId)
            }
        }
    }

    override suspend fun history(): List<WalletLedgerEntry> =
        walletIdSource.walletId()?.let { ledger.history(it) }.orEmpty()

    override suspend fun recordPrototypeEntry(entry: WalletLedgerEntry) {
        walletIdSource.walletId()?.let { ledger.upsert(it, entry) }
    }
}
