package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.request.CreatePlusWalletPurchaseRequest
import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.exception.PlusRequestRejected
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PlusPurchaseService(
    private val wallet: PlusWalletClient,
    private val properties: PlusConfigurationProperties,
) {
    fun purchase(
        userId: UUID,
        authenticatedUserId: UUID,
        walletId: UUID,
        planCode: String,
        idempotencyKey: String,
    ): PlusPurchaseSnapshot {
        if (userId != authenticatedUserId) throw CustomerAccessDenied()
        if (idempotencyKey.isBlank() || idempotencyKey.length > 180) {
            throw PlusRequestRejected("A valid idempotency key is required")
        }
        val normalizedPlan = planCode.trim().uppercase()
        val plan = properties.plans.entries
            .firstOrNull { it.key.uppercase() == normalizedPlan }
            ?.value
            ?: throw PlusRequestRejected("Plus plan is not available")
        return wallet.create(
            walletId,
            idempotencyKey,
            CreatePlusWalletPurchaseRequest(
                userId, normalizedPlan, plan.priceMinor, plan.durationDays,
                plan.passCount, plan.passDiscountMinor, plan.serviceCode,
            ),
        )
    }
}
