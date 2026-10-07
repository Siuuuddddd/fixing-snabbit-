package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.ClaimedDelivery
import org.springframework.stereotype.Component

@Component
class NotificationPushMessageFactory(private val objectMapper: ObjectMapper) {
    fun create(delivery: ClaimedDelivery): PushMessage {
        val payload = objectMapper.readTree(delivery.payload)
        val data = linkedMapOf("type" to delivery.eventType)
        copyText(payload, "bookingId", data)
        copyText(payload, "walletId", data)
        val job = payload.path("jobs").firstOrNull() ?: payload.path("offers").firstOrNull()
        if (job != null) {
            copyText(job, "jobId", data)
            copyText(job, "visitIndex", data)
            if (delivery.recipientType == RecipientType.WORKER && "jobId" !in data) {
                copyText(job, "slotHoldId", data, "jobId")
            }
        }
        val channelId = channelId(delivery)
        data["channelId"] = channelId
        return PushMessage(delivery.title, delivery.body, data, channelId)
    }

    private fun copyText(node: JsonNode, source: String, target: MutableMap<String, String>, key: String = source) {
        node.path(source).asText().takeIf(String::isNotBlank)?.let { target[key] = it }
    }

    private fun channelId(delivery: ClaimedDelivery): String = when {
        delivery.recipientType == RecipientType.WORKER -> "jobs"
        delivery.eventType.contains("Wallet", ignoreCase = true) ||
            delivery.eventType.contains("Payment", ignoreCase = true) ||
            delivery.eventType.contains("Refund", ignoreCase = true) ||
            delivery.eventType.contains("Plus", ignoreCase = true) -> "payments"
        else -> "bookings"
    }
}
