package com.snabbit.prototype.support.service

import com.snabbit.prototype.support.dto.response.BookingOwnershipContext
import com.snabbit.prototype.support.exception.SupportRequestRejected
import com.snabbit.prototype.support.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class SupportBookingClient(factory: InternalServiceClientFactory, properties: SupportIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun ownership(bookingId: UUID): BookingOwnershipContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/ownership", bookingId)
        .retrieve()
        .body(BookingOwnershipContext::class.java)
        ?: throw SupportRequestRejected("Booking Service returned an empty booking context")
}
