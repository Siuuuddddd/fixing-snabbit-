package com.snabbit.prototype.mobile.navigation

import com.snabbit.prototype.mobile.BuildConfig

enum class AppRole {
    CUSTOMER,
    WORKER;

    companion object {
        fun fromBuild(): AppRole = if (BuildConfig.APP_ROLE == "worker") WORKER else CUSTOMER
    }
}
