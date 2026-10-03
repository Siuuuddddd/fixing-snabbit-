package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.dto.response.BookingRatingContext
import com.snabbit.prototype.user.exception.CustomerRatingRejected
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class CustomerRatingBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): BookingRatingContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/rating-context", bookingId)
        .retrieve()
        .body(BookingRatingContext::class.java)
        ?: throw CustomerRatingRejected("Booking Service returned an empty rating context")
}
