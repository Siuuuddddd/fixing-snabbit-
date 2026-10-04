package com.snabbit.prototype.mobile.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.snabbit.prototype.mobile.ui.components.SnabbitBottomBar
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun SnabbitApp(role: AppRole = AppRole.fromBuild()) {
    when (role) {
        AppRole.CUSTOMER -> CustomerShell()
        AppRole.WORKER -> WorkerShell()
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun CustomerShell() {
    var selected by rememberSaveable { mutableStateOf(CustomerTab.HOME) }
    val home = rememberNavBackStack(HomeRoute)
    val bookings = rememberNavBackStack(MyBookingsRoute)
    val wallet = rememberNavBackStack(AddMoneyRoute)
    val plus = rememberNavBackStack(SnabbitPlusRoute)
    val profile = rememberNavBackStack(ProfileRoute)
    val stacks = mapOf(
        CustomerTab.HOME to home,
        CustomerTab.BOOKINGS to bookings,
        CustomerTab.WALLET to wallet,
        CustomerTab.PLUS to plus,
        CustomerTab.PROFILE to profile,
    )
    AppShell(
        backStack = stacks.getValue(selected),
        bottomBar = { SnabbitBottomBar(CustomerTab.entries, selected, { selected = it }) },
    )
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun WorkerShell() {
    var selected by rememberSaveable { mutableStateOf(WorkerTab.JOBS) }
    val jobs = rememberNavBackStack(WorkerJobFeedRoute)
    val schedule = rememberNavBackStack(WorkerLandingRoute)
    val earnings = rememberNavBackStack(WorkerEarningsRoute)
    val support = rememberNavBackStack(EmptyErrorOfflineRoute)
    val profile = rememberNavBackStack(ProfileRoute)
    val stacks = mapOf(
        WorkerTab.JOBS to jobs,
        WorkerTab.SCHEDULE to schedule,
        WorkerTab.EARNINGS to earnings,
        WorkerTab.SUPPORT to support,
        WorkerTab.PROFILE to profile,
    )
    AppShell(
        backStack = stacks.getValue(selected),
        bottomBar = { SnabbitBottomBar(WorkerTab.entries, selected, { selected = it }) },
    )
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun AppShell(backStack: NavBackStack<NavKey>, bottomBar: @Composable () -> Unit) {
    Scaffold(bottomBar = bottomBar) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = koinEntryProvider(),
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        )
    }
}
