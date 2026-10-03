package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.dto.response.BookingRatingContext

import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.repository.CustomerRatingRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import com.snabbit.prototype.user.events.UserOutboxWriter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

@Component
class CustomerRatingBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun context(bookingId: UUID): BookingRatingContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/rating-context", bookingId)
        .retrieve()
        .body(BookingRatingContext::class.java)
        ?: throw CustomerRatingRejected("Booking Service returned an empty rating context")
}
