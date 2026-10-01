package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.config.IntegrationProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
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

data class WorkerMatchRequest(
    val serviceCode: String,
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

@Component
class MatchingClients(builder: RestClient.Builder, properties: IntegrationProperties) {
    private val userClient = builder.clone().baseUrl(properties.userServiceUrl).build()
    private val workerClient = builder.clone().baseUrl(properties.workerServiceUrl).build()

    suspend fun household(userId: UUID): HouseholdSnapshotResponse = withContext(Dispatchers.IO) {
        userClient.get()
            .uri("/internal/v1/users/{userId}/household", userId)
            .retrieve()
            .body(HouseholdSnapshotResponse::class.java)
            ?: error("User Service returned an empty household snapshot")
    }

    suspend fun matches(request: WorkerMatchRequest): List<WorkerMatchResponse> = withContext(Dispatchers.IO) {
        workerClient.post()
            .uri("/internal/v1/workers/matches")
            .body(request)
            .retrieve()
            .body(object : ParameterizedTypeReference<List<WorkerMatchResponse>>() {})
            ?: emptyList()
    }
}
