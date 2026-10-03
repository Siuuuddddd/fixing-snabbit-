package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.dto.response.ProviderRefund

import com.snabbit.prototype.wallet.dto.response.ProviderOrder

import com.fasterxml.jackson.databind.JsonNode
import com.snabbit.prototype.wallet.config.WalletProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RazorpayPaymentProvider(
    restClientBuilder: RestClient.Builder,
    private val properties: WalletProperties,
) : PaymentProvider {
    private val fakeOrders = ConcurrentHashMap<String, ProviderOrder>()
    private val client = restClientBuilder
        .baseUrl(properties.razorpay.baseUrl)
        .defaultHeaders { headers ->
            if (properties.razorpay.usesProviderApi()) {
                headers.setBasicAuth(properties.razorpay.keyId, properties.razorpay.keySecret)
            }
        }
        .build()

    override fun findOrderByReceipt(receipt: String): ProviderOrder? {
        requireValidReceipt(receipt)
        if (!properties.razorpay.usesProviderApi()) {
            return fakeOrders[receipt]
        }
        requireCredentials()
        return try {
            val response = client.get()
                .uri { builder ->
                    builder.path("/v1/orders")
                        .queryParam("receipt", receipt)
                        .queryParam("count", 2)
                        .build()
                }
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw PaymentProviderException("Razorpay returned an empty order lookup response")
            val items = response.path("items")
            if (!items.isArray) throw PaymentProviderException("Razorpay order lookup response is missing items")
            val exactMatches = items.filter { item -> item.path("receipt").asText() == receipt }
            if (exactMatches.size > 1) {
                throw PaymentProviderException("Razorpay returned multiple orders for one receipt")
            }
            exactMatches.singleOrNull()?.toProviderOrder()
        } catch (error: PaymentProviderException) {
            throw error
        } catch (error: Exception) {
            throw PaymentProviderException("Razorpay order lookup failed", error)
        }
    }

    override fun createOrder(amountMinor: Long, receipt: String): ProviderOrder {
        requireValidReceipt(receipt)
        if (!properties.razorpay.usesProviderApi()) {
            return fakeOrders.computeIfAbsent(receipt) {
                ProviderOrder("order_fake_${UUID.randomUUID()}", amountMinor, properties.currency)
            }
        }
        requireCredentials()
        return try {
            val response = client.post()
                .uri("/v1/orders")
                .body(
                    mapOf(
                        "amount" to amountMinor,
                        "currency" to properties.currency,
                        "receipt" to receipt,
                    ),
                )
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw PaymentProviderException("Razorpay returned an empty order response")
            response.toProviderOrder()
        } catch (error: PaymentProviderException) {
            throw error
        } catch (error: Exception) {
            throw PaymentProviderException("Razorpay order creation failed", error)
        }
    }

    override fun refund(paymentId: String, amountMinor: Long, reference: String): ProviderRefund {
        require(reference.length >= 10 && REFUND_REFERENCE_PATTERN.matches(reference)) {
            "Refund reference must use at least 10 letters, numbers, hyphens or underscores"
        }
        if (!properties.razorpay.usesProviderApi()) {
            val deterministicId = UUID.nameUUIDFromBytes(reference.toByteArray(StandardCharsets.UTF_8))
            return ProviderRefund("rfnd_fake_$deterministicId", amountMinor)
        }
        requireCredentials()
        return try {
            val response = client.post()
                .uri("/v1/payments/{paymentId}/refund", paymentId)
                .header(REFUND_IDEMPOTENCY_HEADER, reference)
                .body(
                    mapOf(
                        "amount" to amountMinor,
                        "notes" to mapOf(REFUND_REFERENCE_NOTE to reference),
                    ),
                )
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw PaymentProviderException("Razorpay returned an empty refund response")
            ProviderRefund(response.requiredText("id"), response.requiredLong("amount"))
        } catch (error: PaymentProviderException) {
            throw error
        } catch (error: Exception) {
            throw PaymentProviderException("Razorpay refund failed", error)
        }
    }

    private fun requireCredentials() {
        if (properties.razorpay.keyId.isBlank() || properties.razorpay.keySecret.isBlank()) {
            throw PaymentProviderException("Razorpay credentials are required in real mode")
        }
    }

    private fun requireValidReceipt(receipt: String) {
        require(receipt.isNotBlank() && receipt.length <= MAX_RECEIPT_LENGTH) {
            "Order receipt must contain between 1 and $MAX_RECEIPT_LENGTH characters"
        }
    }

    private fun JsonNode.toProviderOrder() = ProviderOrder(
        id = requiredText("id"),
        amountMinor = requiredLong("amount"),
        currency = requiredText("currency"),
    )

    private fun JsonNode.requiredText(field: String): String =
        path(field).takeUnless { it.isMissingNode || it.asText().isBlank() }?.asText()
            ?: throw PaymentProviderException("Razorpay response is missing $field")

    private fun JsonNode.requiredLong(field: String): Long =
        path(field).takeUnless { it.isMissingNode || !it.canConvertToLong() }?.asLong()
            ?: throw PaymentProviderException("Razorpay response is missing $field")

    private companion object {
        const val REFUND_IDEMPOTENCY_HEADER = "X-Refund-Idempotency"
        const val REFUND_REFERENCE_NOTE = "snabbit_refund_id"
        const val MAX_RECEIPT_LENGTH = 40
        val REFUND_REFERENCE_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
