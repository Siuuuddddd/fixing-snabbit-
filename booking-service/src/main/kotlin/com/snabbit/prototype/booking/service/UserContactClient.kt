package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.dto.response.BookerContactSnapshot
import com.snabbit.prototype.booking.exception.HomeContactResolutionUnavailable
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class UserContactClient(
    factory: InternalServiceClientFactory,
    properties: IntegrationProperties,
) {
    private val client = factory.create(properties.userServiceUrl)

    fun contact(customerId: UUID): BookerContactSnapshot = try {
        client.get()
            .uri("/internal/v1/users/{userId}/contact", customerId)
            .retrieve()
            .body(BookerContactSnapshot::class.java)
            ?: throw HomeContactResolutionUnavailable("Booker contact is temporarily unavailable")
    } catch (error: HomeContactResolutionUnavailable) {
        throw error
    } catch (error: RestClientException) {
        throw HomeContactResolutionUnavailable("Booker contact is temporarily unavailable")
    }
}
