package com.snabbit.prototype.mobile

import android.app.Application
import com.snabbit.prototype.mobile.data.notification.NotificationChannels
import com.snabbit.prototype.mobile.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SnabbitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        startKoin {
            androidContext(this@SnabbitApplication)
            modules(appModules)
        }
    }
}
