package com.snabbit.prototype.mobile.domain.payment

import kotlinx.coroutines.flow.Flow

enum class CheckoutPurpose { WALLET_TOP_UP, PLUS_MEMBERSHIP }

data class CheckoutRequest(
    val requestId: String,
    val purpose: CheckoutPurpose,
    val providerOrderId: String,
    val amountMinor: Long,
    val description: String,
)

sealed interface CheckoutResult {
    val requestId: String

    data class ClientSuccess(override val requestId: String) : CheckoutResult
    data class ClientFailure(
        override val requestId: String,
        val code: Int,
        val description: String?,
    ) : CheckoutResult
}

interface PaymentCheckoutCoordinator {
    val requests: Flow<CheckoutRequest>
    val results: Flow<CheckoutResult>
    fun request(checkout: CheckoutRequest)
    fun report(result: CheckoutResult)
}
