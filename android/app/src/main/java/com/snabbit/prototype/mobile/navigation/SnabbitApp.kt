package com.snabbit.prototype.mobile.navigation

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.domain.profile.PhoneRequiredEvents
import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.ui.components.SnabbitBottomBar
import com.snabbit.prototype.mobile.ui.login.LoginScreen
import com.snabbit.prototype.mobile.ui.login.LoginUiEvent
import com.snabbit.prototype.mobile.ui.login.LoginViewModel
import com.snabbit.prototype.mobile.ui.login.CustomerLoginScreen
import com.snabbit.prototype.mobile.ui.login.CustomerLoginUiEvent
import com.snabbit.prototype.mobile.ui.login.CustomerLoginViewModel
import com.snabbit.prototype.mobile.ui.login.PhoneOtpScreen
import com.snabbit.prototype.mobile.ui.login.PhoneOtpUiEvent
import com.snabbit.prototype.mobile.ui.login.PhoneOtpViewModel
import com.snabbit.prototype.mobile.ui.login.WorkerPickerScreen
import com.snabbit.prototype.mobile.ui.login.WorkerPickerViewModel
import com.snabbit.prototype.mobile.ui.emailauth.AuthUiEvent
import com.snabbit.prototype.mobile.ui.emailauth.AuthViewModel
import com.snabbit.prototype.mobile.ui.emailauth.CustomerAuthScreen
import com.snabbit.prototype.mobile.ui.emailauth.ForgotPasswordScreen
import com.snabbit.prototype.mobile.ui.emailauth.ForgotPasswordUiEvent
import com.snabbit.prototype.mobile.ui.emailauth.ForgotPasswordViewModel
import com.snabbit.prototype.mobile.ui.emailauth.VerifyEmailScreen
import com.snabbit.prototype.mobile.ui.emailauth.VerifyEmailUiEvent
import com.snabbit.prototype.mobile.ui.emailauth.VerifyEmailViewModel
import com.snabbit.prototype.mobile.ui.profile.PhoneRequiredDialog
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingScreen
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingViewModel
import com.snabbit.prototype.mobile.ui.splash.SplashScreen
import com.snabbit.prototype.mobile.ui.splash.SplashViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

@OptIn(KoinExperimentalAPI::class)
@Composable
fun SnabbitApp(
    role: AppRole = AppRole.fromBuild(),
    googleIdTokenProvider: GoogleIdTokenProvider,
) {
    CompositionLocalProvider(LocalGoogleIdTokenProvider provides googleIdTokenProvider) {
        SnabbitAppContent(role, googleIdTokenProvider)
    }
}

val LocalGoogleIdTokenProvider = staticCompositionLocalOf<GoogleIdTokenProvider> {
    error("GoogleIdTokenProvider is not available")
}

@Composable
private fun SnabbitAppContent(role: AppRole, googleIdTokenProvider: GoogleIdTokenProvider) {
    var authenticated by rememberSaveable { mutableStateOf(false) }
    val sessionManager = koinInject<SessionManager>()
    val notifications = koinInject<NotificationRepository>()
    val pushNavigation = koinInject<PushNavigationCoordinator>()
    val phoneRequiredEvents = koinInject<PhoneRequiredEvents>()
    val profileRepository = koinInject<ProfileRepository>()
    val scope = rememberCoroutineScope()
    var showPhoneRequired by rememberSaveable { mutableStateOf(false) }
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var phoneInvalid by rememberSaveable { mutableStateOf(false) }
    var phoneSaving by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionManager) {
        sessionManager.signedOut.collect { authenticated = false }
    }
    LaunchedEffect(authenticated) {
        if (authenticated) runCatching { notifications.registerCurrentDevice() }
    }
    LaunchedEffect(phoneRequiredEvents) {
        phoneRequiredEvents.events.collect { showPhoneRequired = true }
    }
    if (authenticated) {
        when (role) {
            AppRole.CUSTOMER -> CustomerShell(pushNavigation)
            AppRole.WORKER -> WorkerShell(pushNavigation)
        }
    } else {
        AuthenticationFlow(role, googleIdTokenProvider, onAuthenticated = { authenticated = true })
    }
    if (showPhoneRequired && role == AppRole.CUSTOMER) {
        PhoneRequiredDialog(
            phoneDigits = phoneDigits,
            isSaving = phoneSaving,
            isInvalid = phoneInvalid,
            onPhoneChanged = { value ->
                phoneDigits = value.filter(Char::isDigit).take(10)
                phoneInvalid = false
            },
            onSave = {
                if (!Regex("[6-9][0-9]{9}").matches(phoneDigits)) {
                    phoneInvalid = true
                } else {
                    phoneSaving = true
                    scope.launch {
                        runCatching { profileRepository.savePhone("+91$phoneDigits") }
                            .onSuccess { showPhoneRequired = false }
                        phoneSaving = false
                    }
                }
            },
            onDismiss = { showPhoneRequired = false },
        )
    }
}

@Composable
private fun AuthenticationFlow(
    role: AppRole,
    googleIdTokenProvider: GoogleIdTokenProvider,
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
                        LaunchDestination.LOGIN -> backStack.replaceWith(LoginRoute)
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
                    if (state.completed) backStack.replaceWith(LoginRoute)
                }
                OnboardingScreen(
                    state = state,
                    onPageChanged = viewModel::selectPage,
                    onContinue = viewModel::continueOnboarding,
                    onSkip = viewModel::skip,
                )
            }
            entry<LoginRoute> {
                val context = LocalContext.current
                if (role == AppRole.CUSTOMER) {
                    val viewModel = koinViewModel<CustomerLoginViewModel> { parametersOf(googleIdTokenProvider) }
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            when (event) {
                                CustomerLoginUiEvent.Authenticated -> onAuthenticated()
                                CustomerLoginUiEvent.AddGoogleAccount -> context.openGoogleAccountSettings()
                                is CustomerLoginUiEvent.OpenEmail -> backStack.add(EmailAuthRoute(event.rememberMe))
                                is CustomerLoginUiEvent.OpenOtp -> backStack.add(
                                    PhoneOtpRoute(event.phoneNumber, event.rememberMe, event.resendSeconds),
                                )
                            }
                        }
                    }
                    CustomerLoginScreen(
                        state = state,
                        onPhoneChanged = viewModel::updatePhone,
                        onRememberChanged = viewModel::setRememberMe,
                        onContinue = viewModel::continueWithPhone,
                        onGoogle = viewModel::continueWithGoogle,
                        onAddGoogleAccount = viewModel::addGoogleAccount,
                        onEmail = viewModel::openEmail,
                    )
                } else {
                    val googleViewModel = koinViewModel<LoginViewModel> { parametersOf(googleIdTokenProvider) }
                    val googleState by googleViewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(googleViewModel) {
                        googleViewModel.events.collect { event ->
                            when (event) {
                                LoginUiEvent.Authenticated -> onAuthenticated()
                                LoginUiEvent.OpenWorkerPicker -> backStack.replaceWith(WorkerPickerRoute)
                                LoginUiEvent.AddGoogleAccount -> context.openGoogleAccountSettings()
                            }
                        }
                    }
                    LoginScreen(
                        state = googleState,
                        onContinueWithGoogle = googleViewModel::continueWithGoogle,
                        onAddGoogleAccount = googleViewModel::addGoogleAccount,
                    )
                }
            }
            entry<PhoneOtpRoute> { route ->
                val viewModel = koinViewModel<PhoneOtpViewModel> {
                    parametersOf(route.phoneNumber, route.rememberMe, route.resendSeconds)
                }
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        if (event == PhoneOtpUiEvent.Authenticated) onAuthenticated()
                    }
                }
                PhoneOtpScreen(
                    state = state,
                    onCodeChanged = viewModel::updateCode,
                    onVerify = viewModel::submit,
                    onResend = viewModel::resend,
                    onChangeNumber = { backStack.removeLastOrNull() },
                )
            }
            entry<EmailAuthRoute> { route ->
                val emailViewModel = koinViewModel<AuthViewModel> { parametersOf(route.rememberMe) }
                val emailState by emailViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(emailViewModel) {
                    emailViewModel.events.collect { event ->
                        when (event) {
                            AuthUiEvent.Authenticated -> onAuthenticated()
                            is AuthUiEvent.VerifyEmail -> backStack.add(VerifyEmailRoute(event.email, event.rememberMe))
                        }
                    }
                }
                CustomerAuthScreen(
                    state = emailState,
                    googleState = null,
                    onSelectTab = emailViewModel::selectTab,
                    onLoginEmailChanged = emailViewModel::updateLoginEmail,
                    onLoginPasswordChanged = emailViewModel::updateLoginPassword,
                    onSignUpNameChanged = emailViewModel::updateSignUpName,
                    onSignUpEmailChanged = emailViewModel::updateSignUpEmail,
                    onSignUpPasswordChanged = emailViewModel::updateSignUpPassword,
                    onTermsChanged = emailViewModel::setTermsAccepted,
                    onTogglePassword = emailViewModel::togglePasswordVisibility,
                    onEmailBlur = emailViewModel::validateEmailOnBlur,
                    onNameBlur = emailViewModel::validateNameOnBlur,
                    onPasswordBlur = emailViewModel::validatePasswordOnBlur,
                    onSubmit = emailViewModel::submit,
                    onForgotPassword = {
                        val email = emailState.loginEmail.ifBlank { emailState.signUpEmail }
                        backStack.add(ForgotPasswordRoute(email))
                    },
                    onContinueWithGoogle = {},
                    onAddGoogleAccount = {},
                    onTerms = {},
                    onPrivacy = {},
                    onDismissNotice = emailViewModel::dismissNotice,
                )
            }
            entry<VerifyEmailRoute> { route ->
                val viewModel = koinViewModel<VerifyEmailViewModel> { parametersOf(route.email, route.rememberMe) }
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        if (event == VerifyEmailUiEvent.Authenticated) onAuthenticated()
                    }
                }
                VerifyEmailScreen(
                    state = state,
                    onCodeChanged = viewModel::updateCode,
                    onVerify = viewModel::submit,
                    onResend = viewModel::resend,
                    onChangeEmail = { backStack.replaceWith(LoginRoute) },
                )
            }
            entry<ForgotPasswordRoute> { route ->
                val viewModel = koinViewModel<ForgotPasswordViewModel> { parametersOf(route.email) }
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        if (event == ForgotPasswordUiEvent.PasswordUpdated) backStack.replaceWith(LoginRoute)
                    }
                }
                ForgotPasswordScreen(
                    state = state,
                    onEmailChanged = viewModel::updateEmail,
                    onCodeChanged = viewModel::updateCode,
                    onPasswordChanged = viewModel::updatePassword,
                    onTogglePassword = viewModel::togglePasswordVisibility,
                    onEmailBlur = viewModel::validateEmailOnBlur,
                    onPasswordBlur = viewModel::validatePasswordOnBlur,
                    onSubmit = viewModel::submit,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<WorkerPickerRoute> {
                val viewModel = koinViewModel<WorkerPickerViewModel> { parametersOf(googleIdTokenProvider) }
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.authenticated) {
                    if (state.authenticated) onAuthenticated()
                }
                WorkerPickerScreen(state, viewModel::select)
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

private fun android.content.Context.openGoogleAccountSettings() {
    startActivity(
        Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(
            Settings.EXTRA_ACCOUNT_TYPES,
            arrayOf("com.google"),
        ),
    )
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
        openSchedule = { serviceId, addressId -> home.add(ScheduleRoute(serviceId, addressId)) },
        openScheduleStartTime = { serviceId, addressId, dates, durationHours ->
            val destination = ScheduleStartTimeRoute(serviceId, addressId, dates, durationHours)
            if (home.lastOrNull() != destination) home.add(destination)
        },
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
        openWallet = { selected = CustomerTab.WALLET },
        openRewards = { selected = CustomerTab.PLUS },
        openProfile = { selected = CustomerTab.PROFILE },
        back = { if (home.size > 1) home.removeLastOrNull() },
    )
    val profileNavigation = ProfileNavigation(
        openChangePassword = { profile.add(ChangePasswordRoute) },
        back = { if (profile.size > 1) profile.removeLastOrNull() },
    )
    CompositionLocalProvider(
        LocalCustomerNavigation provides navigation,
        LocalProfileNavigation provides profileNavigation,
    ) {
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
    val profileNavigation = ProfileNavigation(
        openChangePassword = {},
        back = { if (profile.size > 1) profile.removeLastOrNull() },
    )
    CompositionLocalProvider(
        LocalWorkerNavigation provides navigation,
        LocalProfileNavigation provides profileNavigation,
    ) {
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
