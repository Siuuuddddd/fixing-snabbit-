package com.snabbit.prototype.mobile.domain.notification

import kotlinx.coroutines.flow.Flow

data class NotificationItem(
    val id: Long,
    val eventType: String,
    val title: String,
    val body: String,
    val critical: Boolean,
    val createdAt: String,
    val readAt: String?,
)

data class DeviceRegistration(
    val deviceId: String,
    val platform: String,
    val active: Boolean,
    val updatedAt: String,
)

interface PushTokenDataSource {
    suspend fun currentToken(): String?
    suspend fun unregister()
}

interface DeviceRegistrationStore {
    suspend fun deviceId(): String?
    suspend fun saveDeviceId(deviceId: String)
    suspend fun clear()
}

interface NotificationRepository {
    suspend fun history(afterNotificationId: Long = 0): List<NotificationItem>
    suspend fun markRead(notificationId: Long): NotificationItem
    suspend fun registerCurrentDevice(): DeviceRegistration?
    suspend fun registerToken(pushToken: String): DeviceRegistration
    suspend fun unregisterCurrentDevice()
}

interface NotificationRefreshEvents {
    val events: Flow<Unit>
    fun notifyChanged()
}
