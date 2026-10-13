package com.snabbit.prototype.user.favourite

import com.snabbit.prototype.user.dto.request.PublicWorkerProfilesRequest
import com.snabbit.prototype.user.dto.response.PublicWorkerProfileSnapshot
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

@Component
class FavouriteWorkerClient(factory: InternalServiceClientFactory, properties: UserIntegrationProperties) {
    private val client = factory.create(properties.workerServiceUrl)

    fun profiles(workerIds: List<UUID>, customerId: UUID?): List<PublicWorkerProfileSnapshot> = client.post()
        .uri("/internal/v1/workers/public-profiles")
        .body(PublicWorkerProfilesRequest(workerIds, customerId))
        .retrieve()
        .body(object : ParameterizedTypeReference<List<PublicWorkerProfileSnapshot>>() {})
        ?: emptyList()
}
