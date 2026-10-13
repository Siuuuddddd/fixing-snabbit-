package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.dto.response.WorkerPrivateContactSnapshot
import com.snabbit.prototype.booking.exception.HomeContactResolutionUnavailable
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class WorkerContactClient(factory: InternalServiceClientFactory, properties: IntegrationProperties) {
    private val client = factory.create(properties.workerServiceUrl)

    fun contact(workerId: UUID): WorkerPrivateContactSnapshot = try {
        client.get().uri("/internal/v1/workers/{workerId}/contact", workerId)
            .retrieve().body(WorkerPrivateContactSnapshot::class.java)
            ?: throw HomeContactResolutionUnavailable("Worker contact is temporarily unavailable")
    } catch (error: HomeContactResolutionUnavailable) {
        throw error
    } catch (_: RestClientException) {
        throw HomeContactResolutionUnavailable("Worker contact is temporarily unavailable")
    }
}
