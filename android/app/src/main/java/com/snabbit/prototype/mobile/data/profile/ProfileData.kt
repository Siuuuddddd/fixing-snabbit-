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
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    private val auth: AuthRepository,
    private val sessionStore: SessionStore,
) : ProfileRepository {
    constructor(
        role: AppRole,
        identity: SessionIdentityProvider,
        addresses: AddressRepository,
        household: HouseholdRepository,
        plus: PlusRepository,
        notifications: NotificationRepository,
        session: SessionManager,
    ) : this(
        role,
        identity,
        addresses,
        household,
        plus,
        notifications,
        object : AuthRepository {
            override suspend fun requestOtp(phoneNumber: String) = error("unused")
            override suspend fun verifyOtp(phoneNumber: String, otp: String) = error("unused")
            override suspend fun register(phoneNumber: String, displayName: String, registrationToken: String) = Unit
            override suspend fun signOut(allDevices: Boolean) = session.signOut()
        },
        LegacyProfileSessionStore(identity, role),
    )
    override suspend fun summary(): ProfileSummary {
        val subject = identity.customerId() ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)
        val user = sessionStore.current()?.user ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)
        if (role == AppRole.WORKER) return ProfileSummary(
            subject, role.name, null, null, null, null,
            user.name, user.email, user.pictureUrl, user.phoneNumber,
        )
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
                user.name,
                user.email,
                user.pictureUrl,
                user.phoneNumber,
            )
        }
    }

    override suspend fun savePhone(phoneNumber: String): String = auth.saveCustomerPhone(phoneNumber)

    override suspend fun signOut() = signOut(false)

    override suspend fun signOut(allDevices: Boolean) {
        runCatching { notifications.unregisterCurrentDevice() }
        auth.signOut(allDevices)
    }
}

private class LegacyProfileSessionStore(identity: SessionIdentityProvider, role: AppRole) : SessionStore {
    private val value = identity.customerId()?.let { id ->
        AuthSession(
            "legacy",
            "Bearer",
            Long.MAX_VALUE,
            "legacy",
            SessionUser(id, "", "", null, false, role.name),
        )
    }
    override val session: Flow<AuthSession?> = flowOf(value)
    override fun current(): AuthSession? = value
    override fun save(session: AuthSession) = Unit
    override fun clear() = Unit
}
