package com.snabbit.prototype.mobile.data.notification

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snabbit.prototype.mobile.domain.notification.NotificationRefreshEvents
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

@SuppressLint("MissingFirebaseInstanceTokenRefresh") // The current SDK replaces onNewToken with onRegistered/FID.
class SnabbitFirebaseMessagingService : FirebaseMessagingService() {
    private val repository: NotificationRepository by inject()
    private val applicationScope: CoroutineScope by inject(named("applicationScope"))
    private val refreshEvents: NotificationRefreshEvents by inject()

    override fun onRegistered(installationId: String) {
        applicationScope.launch { runCatching { repository.registerToken(installationId) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        refreshEvents.notifyChanged()
    }
}
