package com.snabbit.prototype.wallet.eventing

import com.snabbit.prototype.wallet.application.WalletTransactionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

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
        if (event.eventType != "BookingExpired" && event.eventType != "BookingCancelled") return null
        val bookingId = UUID.fromString(event.aggregateId)
        return transactions.terminateBookingPayment(bookingId, event.eventId)
    }
}
