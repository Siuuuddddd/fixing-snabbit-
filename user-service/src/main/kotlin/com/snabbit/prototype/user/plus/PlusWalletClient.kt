package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.request.CreatePlusWalletPurchaseRequest
import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot
import java.util.UUID

interface PlusWalletClient {
    fun create(walletId: UUID, idempotencyKey: String, request: CreatePlusWalletPurchaseRequest): PlusPurchaseSnapshot
}
