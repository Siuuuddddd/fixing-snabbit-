package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.dto.response.BookingRatingContext
import com.snabbit.prototype.worker.exception.WorkerRatingRejected
import com.snabbit.prototype.worker.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class BookingRatingClient(factory: InternalServiceClientFactory, properties: WorkerIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): BookingRatingContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/rating-context", bookingId)
        .retrieve()
        .body(BookingRatingContext::class.java)
        ?: throw WorkerRatingRejected("Booking Service returned an empty rating context")
}
