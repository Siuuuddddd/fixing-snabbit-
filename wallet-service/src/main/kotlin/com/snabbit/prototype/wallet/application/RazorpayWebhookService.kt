package com.snabbit.prototype.wallet.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.payment.RazorpayWebhookVerifier
import com.snabbit.prototype.wallet.support.InvalidWebhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RazorpayWebhookService(
    private val verifier: RazorpayWebhookVerifier,
    private val objectMapper: ObjectMapper,
    private val transactions: WalletTransactionService,
    private val walletService: WalletService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(rawBody: ByteArray, signature: String, eventId: String): String {
        if (!verifier.verify(rawBody, signature)) throw InvalidWebhook("Razorpay signature verification failed")
        if (eventId.isBlank()) throw InvalidWebhook("Razorpay event ID is required")
        val root = runCatching { objectMapper.readTree(rawBody) }
            .getOrElse { throw InvalidWebhook("Webhook body is not valid JSON") }
        val eventType = root.path("event").asText()
        if (eventType != "payment.captured") return "IGNORED_EVENT"
        val payment = root.path("payload").path("payment").path("entity")
        val providerOrderId = payment.requiredText("order_id")
        val providerPaymentId = payment.requiredText("id")
        val amountMinor = payment.path("amount").takeIf { it.canConvertToLong() }?.asLong()
            ?: throw InvalidWebhook("Webhook payment amount is missing")

        val outcome = transactions.processCapture(
            ProviderCapture(
                eventId = eventId,
                eventType = eventType,
                providerOrderId = providerOrderId,
                providerPaymentId = providerPaymentId,
                amountMinor = amountMinor,
            ),
        )
        outcome.compensationRefundId?.let { refundId ->
            runCatching { walletService.executeRefund(refundId) }
                .onFailure { error -> logger.warn("Late capture refund {} was queued for retry", refundId, error) }
        }
        return outcome.result
    }

    private fun com.fasterxml.jackson.databind.JsonNode.requiredText(field: String): String =
        path(field).asText().takeIf(String::isNotBlank)
            ?: throw InvalidWebhook("Webhook payment $field is missing")
}
