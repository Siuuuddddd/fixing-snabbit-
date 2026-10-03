package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.PlusPassBookingContext

import com.snabbit.prototype.user.domain.enums.PlusPassStatus

import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientResponseException
import java.time.Clock
import java.time.Instant
import java.util.UUID

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
