package com.snabbit.prototype.notification.events

import com.snabbit.prototype.notification.dto.response.BookingOwner
import com.snabbit.prototype.notification.dto.response.ArrivalNotificationContext
import com.snabbit.prototype.notification.dto.response.WalletOwner
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class EventRecipientResolver(factory: InternalServiceClientFactory, properties: NotificationIntegrationProperties) {
    private val bookingClient = factory.create(properties.bookingServiceUrl)
    private val walletClient = factory.create(properties.walletServiceUrl)

    fun customerId(event: EventEnvelope): UUID {
        val bookingId = event.payload.path("bookingId").takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.takeIf(String::isNotBlank)?.let(UUID::fromString)
        if (bookingId != null) {
            return bookingClient.get()
                .uri("/internal/v1/bookings/{bookingId}/ownership", bookingId)
                .retrieve()
                .body(BookingOwner::class.java)
                ?.customerId
                ?: error("Booking Service returned an empty owner")
        }
        val walletId = event.payload.path("walletId").takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.takeIf(String::isNotBlank)?.let(UUID::fromString)
            ?: throw IllegalArgumentException("Notification event has no bookingId or walletId")
        return walletClient.get()
            .uri("/internal/v1/wallets/{walletId}/ownership", walletId)
            .retrieve()
            .body(WalletOwner::class.java)
            ?.customerId
            ?: error("Wallet Service returned an empty owner")
    }

    fun arrivalNotificationContext(event: EventEnvelope): ArrivalNotificationContext {
        val bookingId = event.payload.path("bookingId").asText().takeIf(String::isNotBlank)?.let(UUID::fromString)
            ?: throw IllegalArgumentException("Arrival OTP event has no bookingId")
        return bookingClient.get()
            .uri("/internal/v1/bookings/{bookingId}/arrival-notification-context", bookingId)
            .retrieve()
            .body(ArrivalNotificationContext::class.java)
            ?: error("Booking Service returned an empty arrival notification context")
    }
}
