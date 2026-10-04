package com.snabbit.prototype.mobile.data.payment

import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultPaymentCheckoutCoordinator : PaymentCheckoutCoordinator {
    private val requestEvents = MutableSharedFlow<CheckoutRequest>(extraBufferCapacity = 1)
    private val resultEvents = MutableSharedFlow<CheckoutResult>(extraBufferCapacity = 4)
    override val requests: Flow<CheckoutRequest> = requestEvents
    override val results: Flow<CheckoutResult> = resultEvents
    override fun request(checkout: CheckoutRequest) { requestEvents.tryEmit(checkout) }
    override fun report(result: CheckoutResult) { resultEvents.tryEmit(result) }
}
