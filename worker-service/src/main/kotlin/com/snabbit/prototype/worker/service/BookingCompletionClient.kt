package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.dto.response.WorkerCompletionCountResponse
import com.snabbit.prototype.worker.rating.WorkerIntegrationProperties
import com.snabbit.prototype.worker.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class BookingCompletionClient(
    factory: InternalServiceClientFactory,
    properties: WorkerIntegrationProperties,
) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun completedCount(workerId: UUID): Long = client.get()
        .uri("/internal/v1/bookings/workers/{workerId}/completed-count", workerId)
        .retrieve()
        .body(WorkerCompletionCountResponse::class.java)
        ?.completedJobs
        ?: 0
}
