package com.snabbit.prototype.mobile.data.profile

import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.domain.profile.ProfileSummary
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DefaultProfileRepository(
    private val role: AppRole,
    private val identity: SessionIdentityProvider,
    private val addresses: AddressRepository,
    private val household: HouseholdRepository,
    private val plus: PlusRepository,
    private val notifications: NotificationRepository,
    private val session: SessionManager,
) : ProfileRepository {
    override suspend fun summary(): ProfileSummary {
        val subject = identity.customerId() ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)
        if (role == AppRole.WORKER) return ProfileSummary(subject, role.name, null, null, null, null)
        return coroutineScope {
            val addressCount = async { addresses.addresses().size }
            val preferences = async { household.preferences() }
            val membership = async { plus.status() }
            val householdSnapshot = preferences.await()
            val plusSnapshot = membership.await()
            ProfileSummary(
                subject,
                role.name,
                addressCount.await(),
                householdSnapshot.hasPets || householdSnapshot.petDetails.isNotBlank() || householdSnapshot.foodContext.isNotBlank() ||
                    householdSnapshot.utensilPreferences.isNotEmpty() || householdSnapshot.preferredLanguages.isNotEmpty() ||
                    householdSnapshot.allergies.isNotEmpty() || householdSnapshot.religiousFoodRestrictions.isNotEmpty() ||
                    householdSnapshot.accessibilityNeeds.isNotEmpty() || householdSnapshot.workerConstraints.isNotEmpty() ||
                    householdSnapshot.heavyLiftingRequired || householdSnapshot.disallowedChemicals.isNotEmpty() ||
                    householdSnapshot.requiredEquipment.isNotEmpty(),
                plusSnapshot.active,
                plusSnapshot.availablePasses,
            )
        }
    }

    override suspend fun signOut() {
        runCatching { notifications.unregisterCurrentDevice() }
        session.signOut()
    }
}
