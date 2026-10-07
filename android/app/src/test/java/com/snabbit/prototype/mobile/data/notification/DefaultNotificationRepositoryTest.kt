package com.snabbit.prototype.mobile.data.notification

import com.snabbit.prototype.mobile.domain.notification.DeviceRegistrationStore
import com.snabbit.prototype.mobile.domain.notification.PushTokenDataSource
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotificationRepositoryTest {
    @Test fun `current firebase registration is posted and persisted for customer`() = runTest {
        val api = FakeNotificationApi()
        val store = FakeDeviceStore()
        val token = FakePushTokenDataSource()
        val repository = DefaultNotificationRepository(api, AppRole.CUSTOMER, token, store, Json)

        val registration = repository.registerCurrentDevice()

        assertEquals("firebase-installation-id-123", api.registeredToken)
        assertEquals("device", registration?.deviceId)
        assertEquals("device", store.id)
        repository.unregisterCurrentDevice()
        assertTrue(token.unregistered)
        assertEquals(null, store.id)
    }

    @Test fun `refreshed firebase registration uses worker endpoint`() = runTest {
        val api = FakeNotificationApi()
        val repository = DefaultNotificationRepository(
            api, AppRole.WORKER, FakePushTokenDataSource(), FakeDeviceStore(), Json,
        )

        repository.registerToken("refreshed-firebase-installation-id-456")

        assertEquals("refreshed-firebase-installation-id-456", api.workerRegisteredToken)
    }
}

private class FakePushTokenDataSource : PushTokenDataSource {
    var unregistered = false
    override suspend fun currentToken() = "firebase-installation-id-123"
    override suspend fun unregister() { unregistered = true }
}

private class FakeDeviceStore : DeviceRegistrationStore {
    var id: String? = null
    override suspend fun deviceId() = id
    override suspend fun saveDeviceId(deviceId: String) { id = deviceId }
    override suspend fun clear() { id = null }
}

private class FakeNotificationApi : NotificationApi {
    var registeredToken: String? = null
    var workerRegisteredToken: String? = null
    override suspend fun customerHistory(afterId: Long) = emptyList<NotificationDto>()
    override suspend fun customerMarkRead(id: Long) = notification(id)
    override suspend fun customerRegister(request: RegisterDeviceDto) = device().also { registeredToken = request.pushToken }
    override suspend fun customerUnregister(id: String) = device(active = false)
    override suspend fun workerHistory(afterId: Long) = emptyList<NotificationDto>()
    override suspend fun workerMarkRead(id: Long) = notification(id)
    override suspend fun workerRegister(request: RegisterDeviceDto) = device().also { workerRegisteredToken = request.pushToken }
    override suspend fun workerUnregister(id: String) = device(active = false)

    private fun notification(id: Long) = NotificationDto(id, "TYPE", "Title", "Body", false, "now")
    private fun device(active: Boolean = true) = DeviceDto("device", "ANDROID", active, "now")
}
