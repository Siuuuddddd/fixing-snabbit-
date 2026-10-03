package com.snabbit.prototype.notification.service

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.domain.Notification
import com.snabbit.prototype.notification.dto.response.NotificationSnapshot
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val transactions: NotificationTransaction,
    private val properties: NotificationProperties,
) {
    fun history(recipientType: RecipientType, recipientId: UUID, afterId: Long) =
        transactions.history(recipientType, recipientId, afterId)
    fun markRead(recipientType: RecipientType, recipientId: UUID, notificationId: Long) =
        transactions.markRead(recipientType, recipientId, notificationId)
    fun registerDevice(recipientType: RecipientType, recipientId: UUID, token: String) =
        transactions.registerDevice(recipientType, recipientId, token)
    fun unregisterDevice(recipientType: RecipientType, recipientId: UUID, deviceId: UUID) =
        transactions.unregisterDevice(recipientType, recipientId, deviceId)

    fun stream(recipientType: RecipientType, recipientId: UUID, afterId: Long): Flow<NotificationSnapshot> = flow {
        var cursor = afterId.coerceAtLeast(0)
        while (currentCoroutineContext().isActive) {
            val batch = withContext(Dispatchers.IO) { transactions.history(recipientType, recipientId, cursor) }
            batch.forEach { notification ->
                emit(notification)
                cursor = notification.notificationId
            }
            delay(properties.streamPollDelay.toMillis())
        }
    }
}
