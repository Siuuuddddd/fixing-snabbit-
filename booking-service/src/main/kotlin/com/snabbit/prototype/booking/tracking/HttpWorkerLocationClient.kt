package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.dto.response.WorkerLocationResponse
import com.snabbit.prototype.booking.exception.WorkerLocationServiceUnavailable
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class HttpWorkerLocationClient(
    factory: InternalServiceClientFactory,
    properties: IntegrationProperties,
) : WorkerLocationClient {
    private val client: RestClient = factory.create(properties.workerServiceUrl)

    override fun latest(workerId: UUID): WorkerLocationResponse? = try {
        client.get()
            .uri("/internal/v1/workers/{workerId}/location", workerId)
            .retrieve()
            .body(WorkerLocationResponse::class.java)
    } catch (_: HttpClientErrorException.NotFound) {
        null
    } catch (error: RestClientException) {
        throw WorkerLocationServiceUnavailable(error)
    }
}
