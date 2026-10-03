package com.snabbit.prototype.booking.events

import com.snabbit.prototype.booking.service.BookingLifecycleService
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WalletEventHandler(
    private val processedMessages: ProcessedMessageStore,
    private val bookingService: BookingLifecycleService,
    private val properties: EventingProperties,
    private val clock: Clock,
) {
    @Transactional
    fun handle(event: EventEnvelope) {
        if (!processedMessages.claim(properties.consumerGroup, event.eventId, clock.instant())) return
        val bookingId = UUID.fromString(event.aggregateId)
        when (event.eventType) {
            "WalletPaymentSucceeded" -> {
                val paymentId = event.payload.path("paymentId").asText()
                require(paymentId.isNotBlank()) { "WalletPaymentSucceeded is missing paymentId" }
                bookingService.confirmPayment(bookingId, "wallet:$paymentId")
            }
            "WalletPaymentFailed" -> bookingService.cancel(bookingId)
        }
    }
}
