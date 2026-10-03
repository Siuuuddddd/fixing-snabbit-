package com.snabbit.prototype.wallet.controller

import com.snabbit.prototype.wallet.service.RazorpayWebhookService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class RazorpayWebhookController(private val webhookService: RazorpayWebhookService) {
    data class WebhookAcknowledgement(val result: String)

    @PostMapping("/webhooks/razorpay")
    fun webhook(
        @RequestHeader("X-Razorpay-Signature") signature: String,
        @RequestHeader("X-Razorpay-Event-Id") eventId: String,
        @RequestBody rawBody: ByteArray,
    ): WebhookAcknowledgement = WebhookAcknowledgement(webhookService.handle(rawBody, signature, eventId))
}
