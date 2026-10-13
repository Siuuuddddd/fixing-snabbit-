package com.snabbit.prototype.user.favourite

import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

@Component
class FavouriteBookingClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun completedWorkerIds(customerId: UUID): List<UUID> = client.get()
        .uri("/internal/v1/bookings/customers/{customerId}/completed-workers", customerId)
        .retrieve()
        .body(object : ParameterizedTypeReference<List<UUID>>() {})
        ?: emptyList()
}
