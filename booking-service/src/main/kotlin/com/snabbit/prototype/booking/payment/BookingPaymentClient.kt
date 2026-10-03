package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand
import com.snabbit.prototype.booking.dto.request.WalletBookingPaymentRequest
import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot
import com.snabbit.prototype.booking.exception.PaymentRequestRejected
import com.snabbit.prototype.booking.exception.PaymentServiceUnavailable
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

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
