package com.snabbit.prototype.wallet.events

import com.snabbit.prototype.wallet.service.WalletTransactionService
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookingEventHandler(
    private val processedMessages: ProcessedMessageStore,
    private val transactions: WalletTransactionService,
    private val properties: EventingProperties,
    private val clock: Clock,
) {
    @Transactional
    fun handle(event: EventEnvelope): UUID? {
        if (!processedMessages.claim(properties.consumerGroup, event.eventId, clock.instant())) return null
        if (event.eventType != "BookingExpired" &&
            event.eventType != "BookingCancelled" &&
            event.eventType != "BookingCustomerCancelled" &&
            event.eventType != "BookingPlatformCancelled"
            && event.eventType != "VisitRefundRequested"
        ) return null
        val bookingId = UUID.fromString(event.aggregateId)
        val requestedRefundMinor = if (
            event.eventType == "BookingCustomerCancelled" || event.eventType == "BookingPlatformCancelled"
        ) {
            event.payload.path("refundAmountMinor").takeUnless { it.isMissingNode || it.isNull }?.asLong()
                ?: error("BookingCustomerCancelled is missing refundAmountMinor")
        } else {
            null
        }
        val visitRefundMinor = if (event.eventType == "VisitRefundRequested") {
            event.payload.path("amountMinor").asLong(-1).also { require(it >= 0) }
        } else null
        return if (visitRefundMinor != null) {
            val key = event.payload.path("refundIdempotencyKey").asText()
            require(key.isNotBlank()) { "VisitRefundRequested is missing refundIdempotencyKey" }
            transactions.preparePartialRefund(bookingId, visitRefundMinor, key)
        } else {
            transactions.terminateBookingPayment(bookingId, event.eventId, requestedRefundMinor)
        }
    }
}
