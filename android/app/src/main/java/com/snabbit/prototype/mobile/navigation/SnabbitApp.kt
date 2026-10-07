package com.snabbit.prototype.mobile.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.ui.components.SnabbitBottomBar
import com.snabbit.prototype.mobile.ui.login.LoginScreen
import com.snabbit.prototype.mobile.ui.login.LoginViewModel
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingScreen
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingViewModel
import com.snabbit.prototype.mobile.ui.splash.SplashScreen
import com.snabbit.prototype.mobile.ui.splash.SplashViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun SnabbitApp(role: AppRole = AppRole.fromBuild()) {
    var authenticated by rememberSaveable { mutableStateOf(false) }
    val sessionManager = koinInject<SessionManager>()
    val notifications = koinInject<NotificationRepository>()
    val pushNavigation = koinInject<PushNavigationCoordinator>()
    LaunchedEffect(sessionManager) {
        sessionManager.signedOut.collect { authenticated = false }
    }
    LaunchedEffect(authenticated) {
        if (authenticated) runCatching { notifications.registerCurrentDevice() }
    }
    if (authenticated) {
        when (role) {
            AppRole.CUSTOMER -> CustomerShell(pushNavigation)
            AppRole.WORKER -> WorkerShell(pushNavigation)
        }
    } else {
        AuthenticationFlow(role = role, onAuthenticated = { authenticated = true })
    }
}

@Composable
private fun AuthenticationFlow(
    role: AppRole,
    onAuthenticated: () -> Unit,
) {
    val backStack = rememberNavBackStack(SplashRoute)
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SplashRoute> {
                val viewModel = koinViewModel<SplashViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.destination) {
                    when (state.destination) {
                        LaunchDestination.AUTHENTICATED -> onAuthenticated()
                        LaunchDestination.LOGIN -> backStack.replaceWith(LoginOtpRoute)
                        LaunchDestination.ONBOARDING -> backStack.replaceWith(OnboardingRoute)
                        null -> Unit
                    }
                }
                SplashScreen()
            }
            entry<OnboardingRoute> {
                val viewModel = koinViewModel<OnboardingViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.completed) {
                    if (state.completed) backStack.replaceWith(LoginOtpRoute)
                }
                OnboardingScreen(
                    state = state,
                    onPageChanged = viewModel::selectPage,
                    onContinue = viewModel::continueOnboarding,
                    onSkip = viewModel::skip,
                )
            }
            entry<LoginOtpRoute> {
                val viewModel = koinViewModel<LoginViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.authenticated) {
                    if (state.authenticated) onAuthenticated()
                }
                LoginScreen(
                    state = state,
                    role = role,
                    onPhoneChanged = viewModel::updatePhone,
                    onOtpChanged = viewModel::updateOtp,
                    onRequestOtp = viewModel::requestOtp,
                    onVerifyOtp = viewModel::verifyOtp,
                    onResendOtp = viewModel::resendOtp,
                    onEditPhone = viewModel::editPhone,
                )
            }
        },
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
    )
}

private fun NavBackStack<NavKey>.replaceWith(route: NavKey) {
    clear()
    add(route)
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun CustomerShell(pushNavigation: PushNavigationCoordinator) {
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
    val pushDestination by pushNavigation.destination.collectAsStateWithLifecycle()
    LaunchedEffect(pushDestination) {
        when (val destination = pushDestination) {
            is PushDestination.CustomerBooking -> {
                selected = CustomerTab.HOME
                home.clear()
                home.add(HomeRoute)
                home.add(FindingWorkerRoute(destination.bookingId, walletId = null, useWallet = false))
                pushNavigation.consume(destination)
            }
            PushDestination.CustomerWallet -> {
                selected = CustomerTab.WALLET
                pushNavigation.consume(destination)
            }
            else -> Unit
        }
    }
    val navigation = CustomerNavigation(
        openService = { serviceId -> home.add(ServiceDetailsRoute(serviceId)) },
        openAddresses = { serviceId, durationHours -> home.add(AddressServiceabilityRoute(serviceId, durationHours)) },
        openSlots = { serviceId, addressId, durationHours -> home.add(SlotSelectionRoute(serviceId, addressId, durationHours)) },
        openHousehold = { serviceId, addressId, durationHours, bookingType, startsAt ->
            home.add(HouseholdPreferencesRoute(serviceId, addressId, durationHours, bookingType, startsAt))
        },
        openSummary = { serviceId, addressId, durationHours, bookingType, startsAt ->
            home.add(BookingSummaryRoute(serviceId, addressId, durationHours, bookingType, startsAt))
        },
        openFindingWorker = { bookingId, walletId, useWallet -> home.add(FindingWorkerRoute(bookingId, walletId, useWallet)) },
        openTracking = { bookingId, visitIndex -> home.add(LiveTrackingRoute(bookingId, visitIndex)) },
        openCompleted = { bookingId, visitIndex, workerId -> home.add(BookingCompletedRoute(bookingId, visitIndex, workerId)) },
        openCancellation = { bookingId -> home.add(CancelBookingRoute(bookingId)) },
        returnHome = { home.clear(); home.add(HomeRoute) },
        openNotifications = { home.add(NotificationsRoute) },
        back = { if (home.size > 1) home.removeLastOrNull() },
    )
    CompositionLocalProvider(LocalCustomerNavigation provides navigation) {
        AppShell(
            backStack = stacks.getValue(selected),
            bottomBar = { SnabbitBottomBar(CustomerTab.entries, selected, { selected = it }) },
        )
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun WorkerShell(pushNavigation: PushNavigationCoordinator) {
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
    val pushDestination by pushNavigation.destination.collectAsStateWithLifecycle()
    LaunchedEffect(pushDestination) {
        val destination = pushDestination
        if (destination is PushDestination.WorkerJob) {
            selected = WorkerTab.JOBS
            jobs.clear()
            jobs.add(WorkerJobFeedRoute)
            jobs.add(WorkerJobDetailsRoute(destination.bookingId, destination.visitIndex))
            pushNavigation.consume(destination)
        }
    }
    val navigation = WorkerNavigation(
        openJob = { bookingId, visitIndex -> jobs.add(WorkerJobDetailsRoute(bookingId, visitIndex)) },
        back = { if (jobs.size > 1) jobs.removeLastOrNull() },
    )
    CompositionLocalProvider(LocalWorkerNavigation provides navigation) {
        AppShell(
            backStack = stacks.getValue(selected),
            bottomBar = { SnabbitBottomBar(WorkerTab.entries, selected, { selected = it }) },
        )
    }
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
