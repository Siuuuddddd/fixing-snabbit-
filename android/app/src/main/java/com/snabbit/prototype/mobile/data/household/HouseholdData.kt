package com.snabbit.prototype.mobile.data.household

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.household.HouseholdPreferences
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class HouseholdDto(
    val userId: String? = null,
    val plusActive: Boolean = false,
    val favouriteWorkerIds: List<String> = emptyList(),
    val hasPets: Boolean = false,
    val petDetails: String? = null,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
)

@Serializable
data class HouseholdRequestDto(
    val hasPets: Boolean = false,
    val petDetails: String? = null,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
)

interface HouseholdApi {
    @GET("api/v1/users/{userId}/household") suspend fun get(@Path("userId") userId: String): HouseholdDto
    @PUT("api/v1/users/{userId}/household") suspend fun update(@Path("userId") userId: String, @Body request: HouseholdRequestDto): HouseholdDto
}

class DefaultHouseholdRepository(
    private val api: HouseholdApi,
    private val identity: SessionIdentityProvider,
    private val json: Json,
) : HouseholdRepository {
    override suspend fun preferences() = apiCall(json) { api.get(userId()).toDomain() }
    override suspend fun update(preferences: HouseholdPreferences) = apiCall(json) {
        api.update(userId(), preferences.toDto()).toDomain()
    }

    private fun userId() = identity.customerId() ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)
    private fun HouseholdDto.toDomain() = HouseholdPreferences(
        hasPets, petDetails.orEmpty(), foodContext.orEmpty(), utensilPreferences.toSet(),
        preferredLanguages.toSet(), allergies.toSet(), religiousFoodRestrictions.toSet(),
        accessibilityNeeds.toSet(), workerConstraints.toSet(), heavyLiftingRequired,
        disallowedChemicals.toSet(), requiredEquipment.toSet(), plusActive,
    )
    private fun HouseholdPreferences.toDto() = HouseholdRequestDto(
        hasPets = hasPets, petDetails = petDetails.ifBlank { null }, foodContext = foodContext.ifBlank { null },
        utensilPreferences = utensilPreferences.toList(), preferredLanguages = preferredLanguages.toList(),
        allergies = allergies.toList(), religiousFoodRestrictions = religiousFoodRestrictions.toList(),
        accessibilityNeeds = accessibilityNeeds.toList(), workerConstraints = workerConstraints.toList(),
        heavyLiftingRequired = heavyLiftingRequired, disallowedChemicals = disallowedChemicals.toList(),
        requiredEquipment = requiredEquipment.toList(),
    )
}
