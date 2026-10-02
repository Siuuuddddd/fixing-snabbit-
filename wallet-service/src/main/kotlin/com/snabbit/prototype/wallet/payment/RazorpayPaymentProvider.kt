package com.snabbit.prototype.wallet.payment

import com.fasterxml.jackson.databind.JsonNode
import com.snabbit.prototype.wallet.config.WalletProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.util.UUID

@Component
class RazorpayPaymentProvider(
    restClientBuilder: RestClient.Builder,
    private val properties: WalletProperties,
) : PaymentProvider {
    private val client = restClientBuilder
        .baseUrl(properties.razorpay.baseUrl)
        .defaultHeaders { headers ->
            if (properties.razorpay.mode.equals("real", ignoreCase = true)) {
                headers.setBasicAuth(properties.razorpay.keyId, properties.razorpay.keySecret)
            }
        }
        .build()

    override fun createOrder(amountMinor: Long, receipt: String): ProviderOrder {
        if (!properties.razorpay.mode.equals("real", ignoreCase = true)) {
            return ProviderOrder("order_fake_${UUID.randomUUID()}", amountMinor, properties.currency)
        }
        requireCredentials()
        return try {
            val response = client.post()
                .uri("/v1/orders")
                .body(
                    mapOf(
                        "amount" to amountMinor,
                        "currency" to properties.currency,
                        "receipt" to receipt.take(40),
                    ),
                )
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw PaymentProviderException("Razorpay returned an empty order response")
            ProviderOrder(
                id = response.requiredText("id"),
                amountMinor = response.requiredLong("amount"),
                currency = response.requiredText("currency"),
            )
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
        if (!properties.razorpay.mode.equals("real", ignoreCase = true)) {
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

    private fun JsonNode.requiredText(field: String): String =
        path(field).takeUnless { it.isMissingNode || it.asText().isBlank() }?.asText()
            ?: throw PaymentProviderException("Razorpay response is missing $field")

    private fun JsonNode.requiredLong(field: String): Long =
        path(field).takeUnless { it.isMissingNode || !it.canConvertToLong() }?.asLong()
            ?: throw PaymentProviderException("Razorpay response is missing $field")

    private companion object {
        const val REFUND_IDEMPOTENCY_HEADER = "X-Refund-Idempotency"
        const val REFUND_REFERENCE_NOTE = "snabbit_refund_id"
        val REFUND_REFERENCE_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
