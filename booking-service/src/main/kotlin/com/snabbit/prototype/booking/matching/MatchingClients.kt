package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class HouseholdSnapshotResponse(
    val userId: UUID,
    val plusActive: Boolean,
    val favouriteWorkerIds: List<UUID>,
    val hasPets: Boolean,
    val petDetails: String?,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val allergies: List<String>,
    val religiousFoodRestrictions: List<String>,
    val accessibilityNeeds: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
)

data class AddressSnapshotResponse(
    val addressId: UUID,
    val userId: UUID,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val verified: Boolean,
)

data class WorkerMatchRequest(
    val serviceCode: String,
    val basePricePerHourMinor: Long,
    val startsAt: Instant,
    val durationMinutes: Int,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceKm: Double,
    val hasPets: Boolean,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
    val accessibilityNeeds: List<String>,
    val favouriteWorkerIds: List<UUID>,
    val plusActive: Boolean,
)

data class WorkerMatchResponse(
    val workerId: UUID,
    val displayName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val distanceKm: BigDecimal,
    val favourite: Boolean,
    val priceMinor: Long,
)

data class ReservePlusPassRequest(
    val bookingId: UUID,
    val serviceCode: String,
    val grossTotalMinor: Long,
    val holdUntil: Instant,
)

data class PlusPassReservationResponse(
    val passId: UUID?,
    val bookingId: UUID,
    val grossTotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val expiresAt: Instant?,
)

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
