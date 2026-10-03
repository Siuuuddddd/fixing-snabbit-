package com.snabbit.prototype.wallet.booking

import com.snabbit.prototype.wallet.dto.response.BookingPaymentQuote
import com.snabbit.prototype.wallet.exception.BookingQuoteUnavailable
import com.snabbit.prototype.wallet.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class BookingQuoteClient(factory: InternalServiceClientFactory, properties: BookingIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun get(bookingId: UUID): BookingPaymentQuote = try {
        client.get()
            .uri("/internal/v1/bookings/{bookingId}/payment-quote", bookingId)
            .retrieve()
            .body(BookingPaymentQuote::class.java)
            ?: throw BookingQuoteUnavailable("Booking Service returned an empty payment quote")
    } catch (error: RestClientException) {
        throw BookingQuoteUnavailable("Booking quote could not be verified", error)
    }
}
