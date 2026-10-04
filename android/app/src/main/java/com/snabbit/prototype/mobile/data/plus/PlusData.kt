package com.snabbit.prototype.mobile.data.plus

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.plus.PlusPlan
import com.snabbit.prototype.mobile.domain.plus.PlusPlanDataSource
import com.snabbit.prototype.mobile.domain.plus.PlusPurchase
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.plus.PlusStatus
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import com.snabbit.prototype.mobile.domain.wallet.WalletIdDataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable data class PlusStatusDto(
    val userId: String,
    val active: Boolean,
    val expiresAt: String? = null,
    val availablePasses: Long,
)

@Serializable data class PlusPurchaseBody(val walletId: String, val planCode: String)

@Serializable data class PlusPurchaseDto(
    val purchaseId: String,
    val walletId: String,
    val customerId: String,
    val planCode: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String? = null,
)

interface PlusApi {
    @GET("api/v1/users/{userId}/plus")
    suspend fun status(@Path("userId") userId: String): PlusStatusDto

    @POST("api/v1/users/{userId}/plus/purchases")
    suspend fun purchase(
        @Path("userId") userId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: PlusPurchaseBody,
    ): PlusPurchaseDto
}

/** Display-only bridge until the backend exposes a plan catalogue. Purchase authority remains server-side. */
class PrototypePlusPlanDataSource : PlusPlanDataSource {
    override fun plans() = listOf(PlusPlan("MONTHLY", 19_900, 30, 4, 3_000))
}

class DefaultPlusRepository(
    private val api: PlusApi,
    private val identity: SessionIdentityProvider,
    private val walletIdSource: WalletIdDataSource,
    private val planSource: PlusPlanDataSource,
    private val json: Json,
) : PlusRepository {
    override suspend fun status(): PlusStatus {
        val userId = identity.customerId()
            ?: throw DataFailure(DataFailureReason.UNAUTHORIZED, "Sign in to view Snabbit Plus.")
        return apiCall(json) { api.status(userId).let { PlusStatus(it.userId, it.active, it.expiresAt, it.availablePasses) } }
    }

    override suspend fun purchase(planCode: String, idempotencyKey: String): PlusPurchase {
        val userId = identity.customerId()
            ?: throw DataFailure(DataFailureReason.UNAUTHORIZED, "Sign in to join Snabbit Plus.")
        val walletId = walletIdSource.walletId()
            ?: throw DataFailure(DataFailureReason.VALIDATION, "Configure PROTOTYPE_WALLET_ID to join Snabbit Plus.")
        return apiCall(json) {
            api.purchase(userId, idempotencyKey, PlusPurchaseBody(walletId, planCode)).let {
                PlusPurchase(it.purchaseId, it.walletId, it.customerId, it.planCode, it.amountMinor, it.status, it.providerOrderId)
            }
        }
    }

    override fun plans(): List<PlusPlan> = planSource.plans()
}
