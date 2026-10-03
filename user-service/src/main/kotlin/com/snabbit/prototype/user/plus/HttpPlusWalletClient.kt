package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.request.CreatePlusWalletPurchaseRequest
import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.exception.PlusPurchaseUnavailable
import com.snabbit.prototype.user.exception.PlusRequestRejected
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

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
