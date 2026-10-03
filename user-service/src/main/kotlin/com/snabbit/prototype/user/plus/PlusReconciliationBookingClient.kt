package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.PlusPassBookingContext
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Component
class PlusReconciliationBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): PlusPassBookingContext? = try {
        client.get()
            .uri("/internal/v1/bookings/{bookingId}/plus-pass-context", bookingId)
            .retrieve()
            .body(PlusPassBookingContext::class.java)
            ?: error("Booking Service returned an empty Plus pass context")
    } catch (error: RestClientResponseException) {
        if (error.statusCode.value() == 404) null else throw error
    }
}
