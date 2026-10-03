package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.dto.request.ReservePlusPassRequest
import com.snabbit.prototype.booking.dto.request.WorkerMatchRequest
import com.snabbit.prototype.booking.dto.response.AddressSnapshotResponse
import com.snabbit.prototype.booking.dto.response.HouseholdSnapshotResponse
import com.snabbit.prototype.booking.dto.response.PlusPassReservationResponse
import com.snabbit.prototype.booking.dto.response.WorkerMatchResponse
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component

@Component
class MatchingClients(factory: InternalServiceClientFactory, properties: IntegrationProperties) {
    private val userClient = factory.create(properties.userServiceUrl)
    private val workerClient = factory.create(properties.workerServiceUrl)

    suspend fun household(userId: UUID): HouseholdSnapshotResponse = withContext(Dispatchers.IO) {
        userClient.get()
            .uri("/internal/v1/users/{userId}/household", userId)
            .retrieve()
            .body(HouseholdSnapshotResponse::class.java)
            ?: error("User Service returned an empty household snapshot")
    }

    suspend fun address(userId: UUID, addressId: UUID): AddressSnapshotResponse = withContext(Dispatchers.IO) {
        userClient.get()
            .uri("/internal/v1/users/{userId}/addresses/{addressId}", userId, addressId)
            .retrieve()
            .body(AddressSnapshotResponse::class.java)
            ?: error("User Service returned an empty address snapshot")
    }

    suspend fun matches(request: WorkerMatchRequest): List<WorkerMatchResponse> = withContext(Dispatchers.IO) {
        workerClient.post()
            .uri("/internal/v1/workers/matches")
            .body(request)
            .retrieve()
            .body(object : ParameterizedTypeReference<List<WorkerMatchResponse>>() {})
            ?: emptyList()
    }

    suspend fun reservePass(
        userId: UUID,
        request: ReservePlusPassRequest,
    ): PlusPassReservationResponse = withContext(Dispatchers.IO) {
        userClient.post()
            .uri("/internal/v1/users/{userId}/plus/passes/reservations", userId)
            .body(request)
            .retrieve()
            .body(PlusPassReservationResponse::class.java)
            ?: error("User Service returned an empty pass reservation")
    }

    suspend fun releasePass(userId: UUID, bookingId: UUID) = withContext(Dispatchers.IO) {
        userClient.post()
            .uri("/internal/v1/users/{userId}/plus/passes/reservations/{bookingId}/release", userId, bookingId)
            .retrieve()
            .toBodilessEntity()
        Unit
    }
}
