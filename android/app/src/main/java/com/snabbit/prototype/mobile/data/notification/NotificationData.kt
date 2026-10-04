package com.snabbit.prototype.mobile.data.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.notification.DeviceRegistration
import com.snabbit.prototype.mobile.domain.notification.DeviceRegistrationStore
import com.snabbit.prototype.mobile.domain.notification.NotificationItem
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.notification.NotificationRefreshEvents
import com.snabbit.prototype.mobile.domain.notification.PushTokenDataSource
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class NotificationDto(
    val notificationId: Long,
    val eventType: String,
    val title: String,
    val body: String,
    val critical: Boolean,
    val createdAt: String,
    val readAt: String? = null,
)

@Serializable data class RegisterDeviceDto(val pushToken: String)

@Serializable data class DeviceDto(
    val deviceId: String,
    val platform: String,
    val active: Boolean,
    val updatedAt: String,
)

interface NotificationApi {
    @GET("api/v1/notifications") suspend fun customerHistory(@Query("afterNotificationId") afterId: Long): List<NotificationDto>
    @POST("api/v1/notifications/{notificationId}/read") suspend fun customerMarkRead(@Path("notificationId") id: Long): NotificationDto
    @POST("api/v1/notifications/devices") suspend fun customerRegister(@Body request: RegisterDeviceDto): DeviceDto
    @DELETE("api/v1/notifications/devices/{deviceId}") suspend fun customerUnregister(@Path("deviceId") id: String): DeviceDto

    @GET("api/v1/worker/notifications") suspend fun workerHistory(@Query("afterNotificationId") afterId: Long): List<NotificationDto>
    @POST("api/v1/worker/notifications/{notificationId}/read") suspend fun workerMarkRead(@Path("notificationId") id: Long): NotificationDto
    @POST("api/v1/worker/notifications/devices") suspend fun workerRegister(@Body request: RegisterDeviceDto): DeviceDto
    @DELETE("api/v1/worker/notifications/devices/{deviceId}") suspend fun workerUnregister(@Path("deviceId") id: String): DeviceDto
}

class FirebasePushTokenDataSource(private val context: Context) : PushTokenDataSource {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        if (!FirebaseMessaging.getInstance().register().awaitSuccess()) return null
        return FirebaseInstallations.getInstance().id.awaitResult()
    }

    override suspend fun unregister() {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseMessaging.getInstance().unregister().awaitSuccess()
    }

    private suspend fun Task<*>.awaitSuccess(): Boolean = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> if (continuation.isActive) continuation.resume(task.isSuccessful) }
    }

    private suspend fun <T> Task<T>.awaitResult(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}

class DataStoreDeviceRegistrationStore(private val dataStore: DataStore<Preferences>) : DeviceRegistrationStore {
    override suspend fun deviceId(): String? = dataStore.data.map { it[DEVICE_ID] }.first()
    override suspend fun saveDeviceId(deviceId: String) { dataStore.edit { it[DEVICE_ID] = deviceId } }
    override suspend fun clear() { dataStore.edit { it.remove(DEVICE_ID) } }

    private companion object { val DEVICE_ID = stringPreferencesKey("notification_device_id") }
}

class DefaultNotificationRefreshEvents : NotificationRefreshEvents {
    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val events: Flow<Unit> = updates
    override fun notifyChanged() { updates.tryEmit(Unit) }
}

class DefaultNotificationRepository(
    private val api: NotificationApi,
    private val role: AppRole,
    private val tokenSource: PushTokenDataSource,
    private val deviceStore: DeviceRegistrationStore,
    private val json: Json,
) : NotificationRepository {
    override suspend fun history(afterNotificationId: Long): List<NotificationItem> = apiCall(json) {
        val result = if (role == AppRole.WORKER) api.workerHistory(afterNotificationId) else api.customerHistory(afterNotificationId)
        result.map { it.toDomain() }
    }

    override suspend fun markRead(notificationId: Long): NotificationItem = apiCall(json) {
        (if (role == AppRole.WORKER) api.workerMarkRead(notificationId) else api.customerMarkRead(notificationId)).toDomain()
    }

    override suspend fun registerCurrentDevice(): DeviceRegistration? =
        tokenSource.currentToken()?.let { registerToken(it) }

    override suspend fun registerToken(pushToken: String): DeviceRegistration = apiCall(json) {
        val result = if (role == AppRole.WORKER) api.workerRegister(RegisterDeviceDto(pushToken)) else api.customerRegister(RegisterDeviceDto(pushToken))
        result.toDomain().also { deviceStore.saveDeviceId(it.deviceId) }
    }

    override suspend fun unregisterCurrentDevice() {
        val id = deviceStore.deviceId()
        try {
            if (id != null) apiCall(json) { if (role == AppRole.WORKER) api.workerUnregister(id) else api.customerUnregister(id) }
        } finally {
            tokenSource.unregister()
            deviceStore.clear()
        }
    }

    private fun NotificationDto.toDomain() = NotificationItem(notificationId, eventType, title, body, critical, createdAt, readAt)
    private fun DeviceDto.toDomain() = DeviceRegistration(deviceId, platform, active, updatedAt)
}
