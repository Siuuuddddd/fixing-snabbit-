package com.snabbit.prototype.mobile.data.profile

import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.EmailAuthRepository
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.household.HouseholdPreferences
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.notification.DeviceRegistration
import com.snabbit.prototype.mobile.domain.notification.NotificationItem
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.plus.PlusStatus
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePasswordCapabilityTest {
    @Test fun `customer change password capability comes from current customer response`() = runTest {
        val stored = user(hasPassword = false)
        val current = user(hasPassword = true)
        val repository = DefaultProfileRepository(
            AppRole.CUSTOMER,
            SessionIdentityProvider { stored.id },
            EmptyAddresses,
            EmptyHousehold,
            EmptyPlus,
            EmptyNotifications,
            EmptyAuth,
            MemorySessionStore(session(stored)),
            CurrentCustomerAuth(current),
        )

        assertTrue(repository.summary().hasPassword)
    }
}

private object EmptyAddresses : AddressRepository {
    override suspend fun addresses() = emptyList<com.snabbit.prototype.mobile.domain.address.SavedAddress>()
    override suspend fun create(address: NewAddress) = error("unused")
    override suspend fun delete(addressId: String) = Unit
}

private object EmptyHousehold : HouseholdRepository {
    override suspend fun preferences() = HouseholdPreferences()
    override suspend fun update(preferences: HouseholdPreferences) = preferences
}

private object EmptyPlus : PlusRepository {
    override suspend fun status() = PlusStatus("test-id", false, null, 0)
    override suspend fun purchase(planCode: String, idempotencyKey: String) = error("unused")
    override fun plans() = emptyList<com.snabbit.prototype.mobile.domain.plus.PlusPlan>()
}

private object EmptyNotifications : NotificationRepository {
    override suspend fun history(afterNotificationId: Long) = emptyList<NotificationItem>()
    override suspend fun markRead(notificationId: Long) = NotificationItem(notificationId, "TEST", "", "", false, "", null)
    override suspend fun registerCurrentDevice(): DeviceRegistration? = null
    override suspend fun registerToken(pushToken: String) = DeviceRegistration("test-device", "ANDROID", true, "")
    override suspend fun unregisterCurrentDevice() = Unit
}

private object EmptyAuth : AuthRepository

private class CurrentCustomerAuth(private val value: SessionUser) : EmailAuthRepository {
    override suspend fun register(name: String, email: String, password: String, device: AuthDevice) = error("unused")
    override suspend fun verifyEmail(email: String, code: String, device: AuthDevice) = error("unused")
    override suspend fun resendVerification(email: String) = error("unused")
    override suspend fun login(email: String, password: String, device: AuthDevice) = error("unused")
    override suspend fun forgotPassword(email: String) = error("unused")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = error("unused")
    override suspend fun changePassword(currentPassword: String, newPassword: String) = error("unused")
    override suspend fun currentCustomer() = value
}

private class MemorySessionStore(initial: AuthSession) : SessionStore {
    private val value = MutableStateFlow<AuthSession?>(initial)
    override val session = value
    override fun current() = value.value
    override fun save(session: AuthSession) { value.value = session }
    override fun clear() { value.value = null }
}

private fun user(hasPassword: Boolean) = SessionUser(
    "test-id", "Test Customer", "person@example.invalid", null, false, "CUSTOMER", hasPassword = hasPassword,
)
private fun session(user: SessionUser) = AuthSession("test-access", "Bearer", 1L, "test-refresh", user)
