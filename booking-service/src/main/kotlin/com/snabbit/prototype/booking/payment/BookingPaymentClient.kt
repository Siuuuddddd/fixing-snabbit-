package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import com.snabbit.prototype.booking.support.BookingRuleViolation
import com.snabbit.prototype.booking.support.InvalidBookingState
import com.snabbit.prototype.booking.support.PaymentRequestRejected
import com.snabbit.prototype.booking.support.PaymentServiceUnavailable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class StartBookingPaymentCommand(
    val walletId: UUID,
    val bookingId: UUID,
    val authenticatedCustomerId: UUID,
    val useWallet: Boolean,
    val idempotencyKey: String,
)

data class WalletBookingPaymentRequest(
    val bookingId: UUID,
    val useWallet: Boolean,
)

data class BookingPaymentSnapshot(
    val paymentId: UUID,
    val bookingId: UUID,
    val walletId: UUID,
    val totalAmountMinor: Long,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val status: String,
    val providerOrderId: String?,
    val reservationExpiresAt: Instant?,
)

@Component
class BookingPaymentClient(factory: InternalServiceClientFactory, properties: IntegrationProperties) {
    private val walletClient = factory.create(properties.walletServiceUrl)

    fun start(command: StartBookingPaymentCommand): BookingPaymentSnapshot = try {
        walletClient.post()
            .uri("/internal/v1/wallets/{walletId}/booking-payments", command.walletId)
            .header("Idempotency-Key", command.idempotencyKey)
            .body(WalletBookingPaymentRequest(command.bookingId, command.useWallet))
            .retrieve()
            .body(BookingPaymentSnapshot::class.java)
            ?: throw PaymentServiceUnavailable("Wallet Service returned an empty booking payment", IllegalStateException())
    } catch (error: RestClientResponseException) {
        throw PaymentRequestRejected(error.statusCode, "Wallet Service rejected the booking payment")
    } catch (error: RestClientException) {
        throw PaymentServiceUnavailable("Wallet Service is unavailable", error)
    }
}

@Service
class BookingPaymentService(
    private val bookings: BookingLifecycleService,
    private val client: BookingPaymentClient,
    private val clock: Clock,
) {
    fun start(command: StartBookingPaymentCommand): BookingPaymentSnapshot {
        val quote = bookings.paymentQuote(command.bookingId)
        if (quote.customerId != command.authenticatedCustomerId) {
            throw com.snabbit.prototype.booking.support.CustomerAccessDenied()
        }
        if (quote.status != "AWAITING_PAYMENT") {
            throw InvalidBookingState("Booking is not awaiting payment")
        }
        if (!quote.holdExpiresAt.isAfter(clock.instant())) {
            throw InvalidBookingState("Booking slot hold has expired")
        }
        if (quote.quotedTotalMinor <= 0 || quote.currency != "INR") {
            throw BookingRuleViolation("Booking has no valid INR payment quote")
        }
        return client.start(command)
    }
}
