package com.snabbit.prototype.user.plus

import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlusBookingEventHandler(
    private val processed: PlusProcessedMessageStore,
    private val plus: PlusService,
    @param:Value("\${plus-events.consumer-group:user-service-plus}") private val consumerGroup: String,
) {
    @Transactional
    fun handle(event: BookingEventEnvelope) {
        if (!processed.claim(consumerGroup, event.eventId, event.occurredAt)) return
        val bookingId = UUID.fromString(event.aggregateId)
        when (event.eventType) {
            "BookingConfirmed" -> plus.consumePass(bookingId, event.occurredAt)
            "BookingCustomerCancelled", "BookingPlatformCancelled" -> plus.restorePass(bookingId)
            "BookingCancelled", "BookingExpired", "WorkerOfferRejected", "WorkerOfferExpired" ->
                plus.releasePass(bookingId)
        }
    }
}
