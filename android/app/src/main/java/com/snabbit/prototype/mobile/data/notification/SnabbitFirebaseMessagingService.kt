package com.snabbit.prototype.mobile.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.snabbit.prototype.mobile.MainActivity
import com.snabbit.prototype.mobile.R
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
        showNotification(message)
    }

    private fun showNotification(message: RemoteMessage) {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: data["body"] ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val requestCode = (data["bookingId"] ?: data["jobId"] ?: message.messageId.orEmpty()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = data["channelId"] ?: NotificationChannels.forType(data["type"].orEmpty())
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(requestCode, notification)
    }
}
