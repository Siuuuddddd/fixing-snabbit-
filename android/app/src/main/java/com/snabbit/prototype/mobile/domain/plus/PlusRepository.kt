package com.snabbit.prototype.mobile.domain.plus

data class PlusStatus(
    val userId: String,
    val active: Boolean,
    val expiresAt: String?,
    val availablePasses: Long,
)

data class PlusPurchase(
    val purchaseId: String,
    val walletId: String,
    val customerId: String,
    val planCode: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

data class PlusPlan(
    val code: String,
    val priceMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
)

interface PlusPlanDataSource {
    fun plans(): List<PlusPlan>
}

interface PlusRepository {
    suspend fun status(): PlusStatus
    suspend fun purchase(planCode: String, idempotencyKey: String): PlusPurchase
    fun plans(): List<PlusPlan>
}
