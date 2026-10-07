package com.snabbit.prototype.mobile.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.snabbit.prototype.mobile.R

object NotificationChannels {
    const val BOOKINGS = "bookings"
    const val PAYMENTS = "payments"
    const val JOBS = "jobs"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    BOOKINGS,
                    context.getString(R.string.notification_channel_bookings),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    PAYMENTS,
                    context.getString(R.string.notification_channel_payments),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    JOBS,
                    context.getString(R.string.notification_channel_jobs),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    fun forType(type: String): String = when {
        type.contains("Wallet", ignoreCase = true) ||
            type.contains("Payment", ignoreCase = true) ||
            type.contains("Refund", ignoreCase = true) ||
            type.contains("Plus", ignoreCase = true) -> PAYMENTS
        type.contains("Worker", ignoreCase = true) ||
            type.contains("Offer", ignoreCase = true) -> JOBS
        else -> BOOKINGS
    }
}
