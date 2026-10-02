package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import com.snabbit.prototype.user.support.CustomerAccessDenied
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration
import java.util.UUID

data class PlusPlanProperties(
    val priceMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String? = null,
) {
    init {
        require(priceMinor >= 100 && priceMinor % 100 == 0L) {
            "Plus plan price must use positive whole-rupee increments"
        }
        require(durationDays in 1..365) { "Plus plan duration must be between 1 and 365 days" }
        require(passCount in 0..20) { "Plus plan pass count must be between 0 and 20" }
        require(
            passDiscountMinor >= 0 && passDiscountMinor % 100 == 0L &&
                (passCount == 0 || passDiscountMinor >= 100)
        ) {
            "Plus plan pass discount is invalid"
        }
    }
}

@ConfigurationProperties("plus")
data class PlusConfigurationProperties(
    val reconciliationDelay: Duration = Duration.ofMinutes(1),
    val plans: Map<String, PlusPlanProperties> = emptyMap(),
)

data class CreatePlusWalletPurchaseRequest(
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String?,
)

data class PlusPurchaseSnapshot(
    val purchaseId: UUID,
    val walletId: UUID,
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

class PlusPurchaseUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface PlusWalletClient {
    fun create(walletId: UUID, idempotencyKey: String, request: CreatePlusWalletPurchaseRequest): PlusPurchaseSnapshot
}

@Component
class HttpPlusWalletClient(
    factory: InternalServiceClientFactory,
    properties: UserIntegrationProperties,
) : PlusWalletClient {
    private val client: RestClient = factory.create(properties.walletServiceUrl)

    override fun create(
        walletId: UUID,
        idempotencyKey: String,
        request: CreatePlusWalletPurchaseRequest,
    ): PlusPurchaseSnapshot = try {
        client.post()
            .uri("/internal/v1/wallets/{walletId}/plus-purchases", walletId)
            .header("Idempotency-Key", idempotencyKey)
            .body(request)
            .retrieve()
            .body(PlusPurchaseSnapshot::class.java)
            ?: throw PlusPurchaseUnavailable("Wallet Service returned an empty Plus purchase")
    } catch (error: RestClientResponseException) {
        when (error.statusCode) {
            HttpStatus.FORBIDDEN -> throw CustomerAccessDenied()
            HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_ENTITY ->
                throw PlusRequestRejected("Plus purchase was rejected by Wallet Service")
            else -> throw PlusPurchaseUnavailable("Wallet Service could not create the Plus purchase", error)
        }
    }
}

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
