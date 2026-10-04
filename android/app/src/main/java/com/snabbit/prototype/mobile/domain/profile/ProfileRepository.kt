package com.snabbit.prototype.mobile.domain.profile

data class ProfileSummary(
    val subjectId: String,
    val role: String,
    val savedAddressCount: Int?,
    val hasHouseholdPreferences: Boolean?,
    val plusActive: Boolean?,
    val plusPasses: Long?,
)

interface ProfileRepository {
    suspend fun summary(): ProfileSummary
    suspend fun signOut()
}
