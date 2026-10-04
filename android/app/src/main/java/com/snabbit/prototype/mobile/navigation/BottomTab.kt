package com.snabbit.prototype.mobile.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.snabbit.prototype.mobile.R

enum class CustomerTab(@param:StringRes val label: Int, val icon: ImageVector, val root: SnabbitRoute) {
    HOME(R.string.nav_home, Icons.Outlined.Home, HomeRoute),
    BOOKINGS(R.string.nav_bookings, Icons.AutoMirrored.Outlined.List, MyBookingsRoute),
    WALLET(R.string.nav_wallet, Icons.Outlined.AccountBalanceWallet, AddMoneyRoute),
    PLUS(R.string.nav_plus, Icons.Outlined.CalendarMonth, SnabbitPlusRoute),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, ProfileRoute),
}

enum class WorkerTab(@param:StringRes val label: Int, val icon: ImageVector, val root: SnabbitRoute) {
    JOBS(R.string.nav_jobs, Icons.Outlined.WorkOutline, WorkerJobFeedRoute),
    SCHEDULE(R.string.nav_schedule, Icons.Outlined.CalendarMonth, WorkerLandingRoute),
    EARNINGS(R.string.nav_earnings, Icons.Outlined.AccountBalanceWallet, WorkerEarningsRoute),
    SUPPORT(R.string.nav_support, Icons.Outlined.SupportAgent, EmptyErrorOfflineRoute),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, ProfileRoute),
}
