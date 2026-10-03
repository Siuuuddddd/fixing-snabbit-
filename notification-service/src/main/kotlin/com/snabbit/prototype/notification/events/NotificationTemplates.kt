package com.snabbit.prototype.notification.events

import com.snabbit.prototype.notification.dto.response.NotificationTemplate

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.service.NotificationTransaction
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class NotificationTemplates {
    fun forEvent(event: EventEnvelope): NotificationTemplate? = when (event.eventType) {
        "BookingHeld" -> NotificationTemplate("Slot held", "Your service slot is held while a worker responds.", false)
        "WorkerOffersCreated" -> NotificationTemplate("New job offer", "A new home-service job is waiting for your response.", true)
        "BookingAwaitingPayment" -> NotificationTemplate("Worker accepted", "Complete payment before the slot hold expires.", true)
        "WorkerOfferRejected", "WorkerOfferExpired" ->
            NotificationTemplate("Worker unavailable", "We could not confirm this worker. Your held slot was released.", true)
        "BookingConfirmed" -> NotificationTemplate("Booking confirmed", "Payment is verified and your worker is assigned.", true)
        "WorkerArrived" -> NotificationTemplate("Worker arrived", "Your worker has arrived at the service location.", true)
        "ServiceStarted" -> NotificationTemplate("Service started", "Your booked home service is now in progress.", false)
        "ServiceCompleted", "BookingCompleted" ->
            NotificationTemplate("Service completed", "Your service is complete. You can now leave a rating.", false)
        "BookingExpired" -> NotificationTemplate("Booking expired", "The slot expired before confirmation. Any payment will be reversed.", true)
        "BookingCancelled" -> NotificationTemplate("Booking cancelled", "Your unconfirmed booking was cancelled.", false)
        "BookingCustomerCancelled" -> NotificationTemplate("Cancellation accepted", "Your refund is being returned after the cancellation fee.", true)
        "BookingPlatformCancelled" -> NotificationTemplate("Full refund started", "We could not provide the service. A full refund is being returned.", true)
        "WalletTopUpSucceeded" -> NotificationTemplate("Money added", "Your wallet top-up was verified and credited.", true)
        "PlusSubscriptionPurchased" ->
            NotificationTemplate("Plus payment verified", "Your Plus membership purchase was verified.", true)
        "WalletPaymentSucceeded" -> NotificationTemplate("Payment received", "Your booking payment was verified.", false)
        "WalletPaymentFailed" -> NotificationTemplate("Payment stopped", "The booking payment did not complete. Reserved wallet funds were released.", true)
        "WalletPaymentRefundRequired", "WalletRefundQueued" ->
            NotificationTemplate("Refund started", "Your refund has been queued securely.", true)
        "WalletRefundCompleted" -> NotificationTemplate("Refund completed", "Your booking refund has completed.", true)
        else -> null
    }
}
