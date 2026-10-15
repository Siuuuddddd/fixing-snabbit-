package com.snabbit.prototype.mobile.domain.profile

data class ProfileSummary(
    val subjectId: String,
    val role: String,
    val savedAddressCount: Int?,
    val hasHouseholdPreferences: Boolean?,
    val plusActive: Boolean?,
    val plusPasses: Long?,
    val name: String = "",
    val email: String = "",
    val pictureUrl: String? = null,
    val phoneNumber: String? = null,
)

interface ProfileRepository {
    suspend fun summary(): ProfileSummary
    suspend fun savePhone(phoneNumber: String): String = error("Phone update is not implemented")
    suspend fun signOut()
    suspend fun signOut(allDevices: Boolean) = signOut()
}
