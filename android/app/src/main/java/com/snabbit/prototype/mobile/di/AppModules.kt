package com.snabbit.prototype.mobile.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.snabbit.prototype.mobile.BuildConfig
import com.snabbit.prototype.mobile.data.auth.DefaultAuthRepository
import com.snabbit.prototype.mobile.data.auth.EncryptedTokenStore
import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.data.auth.remote.AuthApi
import com.snabbit.prototype.mobile.data.auth.remote.AuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.RetrofitAuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.JwtSessionIdentityProvider
import com.snabbit.prototype.mobile.data.auth.DefaultSessionManager
import com.snabbit.prototype.mobile.data.address.AndroidDeviceLocationDataSource
import com.snabbit.prototype.mobile.data.address.DefaultAddressRepository
import com.snabbit.prototype.mobile.data.address.remote.AddressApi
import com.snabbit.prototype.mobile.data.address.remote.AddressRemoteDataSource
import com.snabbit.prototype.mobile.data.address.remote.RetrofitAddressRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.DefaultCatalogRepository
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogApi
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.remote.RetrofitCatalogRemoteDataSource
import com.snabbit.prototype.mobile.data.home.fake.FakeHabitPackPromoRepository
import com.snabbit.prototype.mobile.data.home.fake.FakeHomeFavoritesRepository
import com.snabbit.prototype.mobile.data.booking.DefaultBookingRepository
import com.snabbit.prototype.mobile.data.booking.remote.BookingApi
import com.snabbit.prototype.mobile.data.bookings.DataStoreKnownBookingDataSource
import com.snabbit.prototype.mobile.data.bookings.DefaultBookingListRepository
import com.snabbit.prototype.mobile.data.notification.DataStoreDeviceRegistrationStore
import com.snabbit.prototype.mobile.data.notification.DefaultNotificationRepository
import com.snabbit.prototype.mobile.data.notification.DefaultNotificationRefreshEvents
import com.snabbit.prototype.mobile.data.notification.FirebasePushTokenDataSource
import com.snabbit.prototype.mobile.data.notification.NotificationApi
import com.snabbit.prototype.mobile.data.profile.DefaultProfileRepository
import com.snabbit.prototype.mobile.data.household.DefaultHouseholdRepository
import com.snabbit.prototype.mobile.data.household.HouseholdApi
import com.snabbit.prototype.mobile.data.wallet.DefaultWalletRepository
import com.snabbit.prototype.mobile.data.wallet.PrototypeConfiguredWalletIdDataSource
import com.snabbit.prototype.mobile.data.wallet.WalletApi
import com.snabbit.prototype.mobile.data.wallet.PrototypeWalletLedgerDataSource
import com.snabbit.prototype.mobile.data.payment.DefaultPaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.data.plus.DefaultPlusRepository
import com.snabbit.prototype.mobile.data.plus.PlusApi
import com.snabbit.prototype.mobile.data.plus.PrototypePlusPlanDataSource
import com.snabbit.prototype.mobile.data.tracking.DefaultTrackingRepository
import com.snabbit.prototype.mobile.data.tracking.OkHttpTrackingStreamDataSource
import com.snabbit.prototype.mobile.data.tracking.TrackingApi
import com.snabbit.prototype.mobile.data.tracking.TrackingStreamDataSource
import com.snabbit.prototype.mobile.data.cancellation.CancellationApi
import com.snabbit.prototype.mobile.data.cancellation.DefaultCancellationRepository
import com.snabbit.prototype.mobile.data.rating.DefaultRatingRepository
import com.snabbit.prototype.mobile.data.rating.RatingApi
import com.snabbit.prototype.mobile.data.worker.DefaultWorkerAvailabilityRepository
import com.snabbit.prototype.mobile.data.worker.DefaultWorkerJobsRepository
import com.snabbit.prototype.mobile.data.worker.DefaultWorkerLocationRepository
import com.snabbit.prototype.mobile.data.worker.WorkerApi
import com.snabbit.prototype.mobile.data.launch.DataStoreOnboardingLocalDataSource
import com.snabbit.prototype.mobile.data.launch.DefaultLaunchRepository
import com.snabbit.prototype.mobile.data.launch.OnboardingLocalDataSource
import com.snabbit.prototype.mobile.data.network.AuthInterceptor
import com.snabbit.prototype.mobile.data.network.RefreshingAuthenticator
import com.snabbit.prototype.mobile.data.network.TokenRefresher
import com.snabbit.prototype.mobile.data.network.UnsupportedTokenRefresher
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.DeviceLocationDataSource
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.home.GetFavoritesUseCase
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.GetHomeServicesUseCase
import com.snabbit.prototype.mobile.domain.home.GetSelectedAddressUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromoRepository
import com.snabbit.prototype.mobile.domain.home.HomeFavoritesRepository
import com.snabbit.prototype.mobile.domain.schedule.GetAvailableSlotsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDateOptionsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.ValidateMultiDaySelectionUseCase
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.bookings.BookingListRepository
import com.snabbit.prototype.mobile.domain.bookings.KnownBookingDataSource
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletIdDataSource
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerDataSource
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.domain.plus.PlusPlanDataSource
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.tracking.TrackingRepository
import com.snabbit.prototype.mobile.domain.cancellation.CancellationRepository
import com.snabbit.prototype.mobile.domain.rating.RatingRepository
import com.snabbit.prototype.mobile.domain.worker.WorkerAvailabilityRepository
import com.snabbit.prototype.mobile.domain.worker.WorkerJobsRepository
import com.snabbit.prototype.mobile.domain.worker.WorkerLocationRepository
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.domain.notification.DeviceRegistrationStore
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.notification.NotificationRefreshEvents
import com.snabbit.prototype.mobile.domain.notification.PushTokenDataSource
import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.navigation.AddMoneyRoute
import com.snabbit.prototype.mobile.navigation.AppRole
import com.snabbit.prototype.mobile.navigation.AddressServiceabilityRoute
import com.snabbit.prototype.mobile.navigation.BookingCompletedRoute
import com.snabbit.prototype.mobile.navigation.BookingSummaryRoute
import com.snabbit.prototype.mobile.navigation.CancelBookingRoute
import com.snabbit.prototype.mobile.navigation.EmptyErrorOfflineRoute
import com.snabbit.prototype.mobile.navigation.FindingWorkerRoute
import com.snabbit.prototype.mobile.navigation.HomeRoute
import com.snabbit.prototype.mobile.navigation.HouseholdPreferencesRoute
import com.snabbit.prototype.mobile.navigation.LiveTrackingRoute
import com.snabbit.prototype.mobile.navigation.MyBookingsRoute
import com.snabbit.prototype.mobile.navigation.NotificationsRoute
import com.snabbit.prototype.mobile.navigation.ProfileRoute
import com.snabbit.prototype.mobile.navigation.ServiceDetailsRoute
import com.snabbit.prototype.mobile.navigation.ScheduleRoute
import com.snabbit.prototype.mobile.navigation.toBookingDraftOrNull
import com.snabbit.prototype.mobile.navigation.SnabbitPlusRoute
import com.snabbit.prototype.mobile.navigation.WorkerEarningsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobDetailsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobFeedRoute
import com.snabbit.prototype.mobile.navigation.WorkerLandingRoute
import com.snabbit.prototype.mobile.navigation.titleRes
import com.snabbit.prototype.mobile.navigation.LocalCustomerNavigation
import com.snabbit.prototype.mobile.navigation.LocalWorkerNavigation
import com.snabbit.prototype.mobile.navigation.PushNavigationCoordinator
import com.snabbit.prototype.mobile.ui.address.AddressScreen
import com.snabbit.prototype.mobile.ui.address.AddressViewModel
import com.snabbit.prototype.mobile.ui.home.HomeScreen
import com.snabbit.prototype.mobile.ui.home.HomeViewModel
import com.snabbit.prototype.mobile.ui.home.HomeUiEvent
import com.snabbit.prototype.mobile.ui.login.LoginViewModel
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingViewModel
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderScreen
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderViewModel
import com.snabbit.prototype.mobile.ui.splash.SplashViewModel
import com.snabbit.prototype.mobile.ui.service.ServiceDetailsScreen
import com.snabbit.prototype.mobile.ui.service.ServiceDetailsViewModel
import com.snabbit.prototype.mobile.ui.booking.BookingSummaryScreen
import com.snabbit.prototype.mobile.ui.booking.BookingSummaryUiState
import com.snabbit.prototype.mobile.ui.booking.BookingSummaryViewModel
import com.snabbit.prototype.mobile.ui.booking.FindingWorkerScreen
import com.snabbit.prototype.mobile.ui.booking.FindingWorkerViewModel
import com.snabbit.prototype.mobile.ui.booking.HouseholdPreferencesScreen
import com.snabbit.prototype.mobile.ui.booking.HouseholdPreferencesViewModel
import com.snabbit.prototype.mobile.ui.booking.ScheduleScreen
import com.snabbit.prototype.mobile.ui.booking.ScheduleViewModel
import com.snabbit.prototype.mobile.ui.tracking.LiveTrackingScreen
import com.snabbit.prototype.mobile.ui.tracking.LiveTrackingViewModel
import com.snabbit.prototype.mobile.ui.completed.BookingCompletedScreen
import com.snabbit.prototype.mobile.ui.completed.BookingCompletedViewModel
import com.snabbit.prototype.mobile.ui.cancellation.CancelBookingScreen
import com.snabbit.prototype.mobile.ui.cancellation.CancelBookingViewModel
import com.snabbit.prototype.mobile.ui.wallet.WalletScreen
import com.snabbit.prototype.mobile.ui.wallet.WalletViewModel
import com.snabbit.prototype.mobile.ui.plus.PlusScreen
import com.snabbit.prototype.mobile.ui.plus.PlusViewModel
import com.snabbit.prototype.mobile.ui.bookings.MyBookingsScreen
import com.snabbit.prototype.mobile.ui.bookings.MyBookingsViewModel
import com.snabbit.prototype.mobile.ui.notification.NotificationsScreen
import com.snabbit.prototype.mobile.ui.notification.NotificationsViewModel
import com.snabbit.prototype.mobile.ui.profile.ProfileScreen
import com.snabbit.prototype.mobile.ui.profile.ProfileViewModel
import com.snabbit.prototype.mobile.ui.common.EmptyErrorOfflineScreen
import com.snabbit.prototype.mobile.ui.worker.WorkerEarningsScreen
import com.snabbit.prototype.mobile.ui.worker.WorkerEarningsViewModel
import com.snabbit.prototype.mobile.ui.worker.WorkerJobDetailsScreen
import com.snabbit.prototype.mobile.ui.worker.WorkerJobDetailsViewModel
import com.snabbit.prototype.mobile.ui.worker.WorkerJobFeedScreen
import com.snabbit.prototype.mobile.ui.worker.WorkerJobFeedViewModel
import com.snabbit.prototype.mobile.ui.worker.WorkerScheduleScreen
import com.snabbit.prototype.mobile.ui.worker.WorkerScheduleViewModel
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.navigation3.navigation
import org.koin.core.qualifier.named
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val storageModule = module {
    single(named("applicationScope")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { androidContext().preferencesDataStoreFile("snabbit_preferences") },
        )
    }
    single<OnboardingLocalDataSource> { DataStoreOnboardingLocalDataSource(get()) }
}

private val networkModule = module {
    single { Json { ignoreUnknownKeys = true; explicitNulls = false } }
    single { AuthInterceptor(get()) }
    single<TokenRefresher> { UnsupportedTokenRefresher(get()) }
    single { RefreshingAuthenticator(get()) }
    single {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
    }
    single {
        OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .authenticator(get<RefreshingAuthenticator>())
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }
    single { get<Retrofit>().create(AuthApi::class.java) }
    single<AuthRemoteDataSource> { RetrofitAuthRemoteDataSource(get()) }
    single { get<Retrofit>().create(CatalogApi::class.java) }
    single<CatalogRemoteDataSource> { RetrofitCatalogRemoteDataSource(get()) }
    single { get<Retrofit>().create(AddressApi::class.java) }
    single<AddressRemoteDataSource> { RetrofitAddressRemoteDataSource(get()) }
    single { get<Retrofit>().create(BookingApi::class.java) }
    single { get<Retrofit>().create(HouseholdApi::class.java) }
    single { get<Retrofit>().create(WalletApi::class.java) }
    single { get<Retrofit>().create(PlusApi::class.java) }
    single { get<Retrofit>().create(TrackingApi::class.java) }
    single { get<Retrofit>().create(CancellationApi::class.java) }
    single { get<Retrofit>().create(RatingApi::class.java) }
    single { get<Retrofit>().create(NotificationApi::class.java) }
    single { get<Retrofit>().create(WorkerApi::class.java) }
    single<TrackingStreamDataSource> { OkHttpTrackingStreamDataSource(get(), get(), BuildConfig.API_BASE_URL) }
}

private val repositoryModule = module {
    single { AppRole.fromBuild() }
    single { PushNavigationCoordinator() }
    single<AuthRepository> { DefaultAuthRepository(get(), get(), get(), get()) }
    single<LaunchRepository> { DefaultLaunchRepository(get(), get()) }
    single<SessionIdentityProvider> { JwtSessionIdentityProvider(get(), get()) }
    single<SessionManager> { DefaultSessionManager(get()) }
    single<CatalogRepository> { DefaultCatalogRepository(get(), get()) }
    single<HomeFavoritesRepository> { FakeHomeFavoritesRepository() }
    single<HabitPackPromoRepository> { FakeHabitPackPromoRepository() }
    single { GetHomeServicesUseCase(get()) }
    single { GetSelectedAddressUseCase(get()) }
    single { GetFavoritesUseCase(get()) }
    single { GetHabitPackPromoUseCase(get()) }
    single { GetDateOptionsUseCase() }
    single { GetDurationsWithPriceUseCase(get()) }
    single { GetAvailableSlotsUseCase(get()) }
    single { ValidateMultiDaySelectionUseCase() }
    single<AddressRepository> { DefaultAddressRepository(get(), get(), get()) }
    single<DeviceLocationDataSource> { AndroidDeviceLocationDataSource(androidContext()) }
    single<KnownBookingDataSource> { DataStoreKnownBookingDataSource(get(), get()) }
    single<BookingRepository> { DefaultBookingRepository(get(), get(), get(), get()) }
    single<BookingListRepository> { DefaultBookingListRepository(get(), get()) }
    single<HouseholdRepository> { DefaultHouseholdRepository(get(), get(), get()) }
    single<WalletIdDataSource> { PrototypeConfiguredWalletIdDataSource(BuildConfig.PROTOTYPE_WALLET_ID) }
    single<WalletLedgerDataSource> { PrototypeWalletLedgerDataSource() }
    single<WalletRepository> { DefaultWalletRepository(get(), get(), get(), get()) }
    single<PaymentCheckoutCoordinator> { DefaultPaymentCheckoutCoordinator() }
    single<PlusPlanDataSource> { PrototypePlusPlanDataSource() }
    single<PlusRepository> { DefaultPlusRepository(get(), get(), get(), get(), get()) }
    single<TrackingRepository> { DefaultTrackingRepository(get(), get(), get()) }
    single<CancellationRepository> { DefaultCancellationRepository(get(), get()) }
    single<RatingRepository> { DefaultRatingRepository(get(), get()) }
    single<PushTokenDataSource> { FirebasePushTokenDataSource(androidContext()) }
    single<DeviceRegistrationStore> { DataStoreDeviceRegistrationStore(get()) }
    single<NotificationRepository> { DefaultNotificationRepository(get(), get(), get(), get(), get()) }
    single<NotificationRefreshEvents> { DefaultNotificationRefreshEvents() }
    single<ProfileRepository> { DefaultProfileRepository(get(), get(), get(), get(), get(), get(), get()) }
    single<WorkerJobsRepository> { DefaultWorkerJobsRepository(get(), get()) }
    single<WorkerAvailabilityRepository> { DefaultWorkerAvailabilityRepository(get(), get()) }
    single<WorkerLocationRepository> { DefaultWorkerLocationRepository(get(), get()) }
}

private val viewModelModule = module {
    viewModel { PlaceholderViewModel() }
    viewModel { SplashViewModel(get()) }
    viewModel { OnboardingViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { (serviceId: String) -> ServiceDetailsViewModel(serviceId, get()) }
    viewModel { AddressViewModel(get(), get()) }
    viewModel { (serviceId: String, addressId: String) ->
        ScheduleViewModel(serviceId, addressId, get(), get(), get(), get(), get())
    }
    viewModel { HouseholdPreferencesViewModel(get()) }
    viewModel { (draft: BookingDraft) -> BookingSummaryViewModel(draft, get(), get(), get()) }
    viewModel { (bookingId: String, walletId: String?, useWallet: Boolean) ->
        FindingWorkerViewModel(bookingId, walletId, useWallet, get())
    }
    viewModel { (bookingId: String, visitIndex: Int) ->
        LiveTrackingViewModel(bookingId, visitIndex, get(), get())
    }
    viewModel { (bookingId: String, visitIndex: Int, workerId: String) ->
        BookingCompletedViewModel(bookingId, visitIndex, workerId, get(), get())
    }
    viewModel { (bookingId: String) -> CancelBookingViewModel(bookingId, get()) }
    viewModel { WalletViewModel(get(), get()) }
    viewModel { PlusViewModel(get(), get()) }
    viewModel { MyBookingsViewModel(get()) }
    viewModel { NotificationsViewModel(get(), get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { WorkerJobFeedViewModel(get()) }
    viewModel { WorkerScheduleViewModel(get()) }
    viewModel { (bookingId: String, visitIndex: Int) -> WorkerJobDetailsViewModel(bookingId, visitIndex, get(), get(), get()) }
    viewModel { WorkerEarningsViewModel() }
}

@OptIn(KoinExperimentalAPI::class)
private val navigationModule = module {
    navigation<HomeRoute> {
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<HomeViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            onEvent = { event ->
                viewModel.onEvent(event)
                when (event) {
                    HomeUiEvent.AddressSelected -> navigation.openAddresses(null, null)
                    HomeUiEvent.WalletSelected -> navigation.openWallet()
                    HomeUiEvent.RewardsSelected,
                    HomeUiEvent.PromoSelected,
                    -> navigation.openRewards()
                    HomeUiEvent.ProfileSelected -> navigation.openProfile()
                    is HomeUiEvent.ServiceSelected -> navigation.openService(event.serviceId)
                    HomeUiEvent.ScheduleSelected -> {
                        val serviceId = state.services.firstNotNullOfOrNull { it.serviceId }
                        if (serviceId != null) {
                            val addressId = state.selectedAddress?.id
                            if (addressId != null) navigation.openSchedule(serviceId, addressId)
                            else navigation.openAddresses(serviceId, 0)
                        }
                    }
                    else -> Unit
                }
            },
        )
    }
    navigation<ServiceDetailsRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<ServiceDetailsViewModel> { parametersOf(route.serviceId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ServiceDetailsScreen(
            state = state,
            onBack = navigation.back,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onDurationSelected = viewModel::selectDuration,
            onContinue = { navigation.openAddresses(route.serviceId, state.selectedDurationHours) },
        )
    }
    navigation<AddressServiceabilityRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<AddressViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AddressScreen(
            state = state,
            onBack = navigation.back,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onShowAddForm = viewModel::showAddForm,
            onHideAddForm = viewModel::hideAddForm,
            onLabelChanged = viewModel::updateLabel,
            onAddressLineChanged = viewModel::updateAddressLine,
            onPincodeChanged = viewModel::updatePincode,
            onCaptureLocation = viewModel::captureLocation,
            onLocationPermissionDenied = viewModel::locationPermissionDenied,
            onSave = viewModel::saveAddress,
            onDelete = viewModel::deleteAddress,
            bookingMode = route.serviceId != null && route.durationHours != null,
            onAddressSelected = { addressId ->
                val serviceId = route.serviceId
                val duration = route.durationHours
                if (serviceId != null && duration != null) navigation.openSchedule(serviceId, addressId)
            },
        )
    }
    navigation<ScheduleRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<ScheduleViewModel> { parametersOf(route.serviceId, route.addressId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ScheduleScreen(
            state = state,
            onBack = navigation.back,
            onEvent = viewModel::onEvent,
            onContinue = {
                viewModel.draftOrNull()?.let { draft ->
                    navigation.openSummary(
                        route.serviceId,
                        route.addressId,
                        draft.durationHours,
                        draft.bookingType.name,
                        draft.startsAt,
                    )
                }
            },
        )
    }
    navigation<HouseholdPreferencesRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<HouseholdPreferencesViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.saved) {
            if (state.saved) {
                viewModel.consumeSaved()
                navigation.openSummary(route.serviceId, route.addressId, route.durationHours, route.bookingType, route.startsAt)
            }
        }
        HouseholdPreferencesScreen(
            state, navigation.back, viewModel::retry, viewModel::setPets, viewModel::setPetDetails,
            viewModel::setFoodContext, viewModel::toggleLanguage, viewModel::toggleUtensil,
            viewModel::toggleAllergy, viewModel::toggleAccessibility, viewModel::toggleEquipment,
            viewModel::toggleConstraint, viewModel::setHeavyLifting, viewModel::save,
        )
    }
    navigation<BookingSummaryRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val draft = route.toBookingDraftOrNull()
        if (draft == null) {
            BookingSummaryScreen(
                state = BookingSummaryUiState(
                    isLoading = false,
                    error = UiDataError(UiDataErrorReason.VALIDATION),
                ),
                onBack = navigation.back,
                onRetry = navigation.back,
                onUseWalletChanged = {},
                onConfirm = {},
            )
        } else {
            val viewModel = koinViewModel<BookingSummaryViewModel> { parametersOf(draft) }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.createdBookingId) {
                state.createdBookingId?.let {
                    viewModel.consumeCreatedBooking()
                    navigation.openFindingWorker(it, state.wallet?.walletId, state.useWallet)
                }
            }
            BookingSummaryScreen(state, navigation.back, viewModel::retry, viewModel::setUseWallet, viewModel::confirm)
        }
    }
    navigation<FindingWorkerRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<FindingWorkerViewModel> { parametersOf(route.bookingId, route.walletId, route.useWallet) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        FindingWorkerScreen(
            state,
            viewModel::retry,
            viewModel::cancel,
            onTrack = { visitIndex -> navigation.openTracking(route.bookingId, visitIndex) },
            onCancelBooking = { navigation.openCancellation(route.bookingId) },
            onCompleted = { visitIndex, workerId -> navigation.openCompleted(route.bookingId, visitIndex, workerId) },
        )
    }
    navigation<LiveTrackingRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<LiveTrackingViewModel> { parametersOf(route.bookingId, route.visitIndex) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.completedWorkerId) {
            state.completedWorkerId?.let { navigation.openCompleted(route.bookingId, route.visitIndex, it) }
        }
        LiveTrackingScreen(state, navigation.back, viewModel::retry, { navigation.openCancellation(route.bookingId) })
    }
    navigation<BookingCompletedRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<BookingCompletedViewModel> { parametersOf(route.bookingId, route.visitIndex, route.workerId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BookingCompletedScreen(
            state, viewModel::retry, viewModel::selectStars, viewModel::updateComment,
            viewModel::submitRating, navigation.returnHome,
        )
    }
    navigation<CancelBookingRoute> { route ->
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<CancelBookingViewModel> { parametersOf(route.bookingId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        CancelBookingScreen(state, navigation.back, viewModel::retry, viewModel::updateReason, viewModel::confirmCancellation)
    }
    navigation<AddMoneyRoute> {
        val viewModel = koinViewModel<WalletViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        WalletScreen(state, viewModel::refresh, viewModel::retry, viewModel::updateAmount, viewModel::addMoney, viewModel::checkAgain)
    }
    navigation<SnabbitPlusRoute> {
        val viewModel = koinViewModel<PlusViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PlusScreen(state, viewModel::refresh, viewModel::retry, viewModel::join, viewModel::checkAgain)
    }
    navigation<MyBookingsRoute> {
        val viewModel = koinViewModel<MyBookingsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MyBookingsScreen(state, viewModel::refresh, viewModel::retry, viewModel::selectFilter)
    }
    navigation<NotificationsRoute> {
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<NotificationsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        NotificationsScreen(state, viewModel::refresh, viewModel::retry, viewModel::selectFilter, viewModel::markRead, viewModel::retryPushRegistration, navigation.back)
    }
    navigation<ProfileRoute> {
        val viewModel = koinViewModel<ProfileViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ProfileScreen(state, viewModel::refresh, viewModel::retry, viewModel::signOut)
    }
    navigation<EmptyErrorOfflineRoute> { EmptyErrorOfflineScreen(onRetry = {}) }
    navigation<WorkerLandingRoute> {
        val viewModel = koinViewModel<WorkerScheduleViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        WorkerScheduleScreen(state, viewModel::retry, viewModel::toggle)
    }
    navigation<WorkerJobFeedRoute> {
        val navigation = LocalWorkerNavigation.current
        val viewModel = koinViewModel<WorkerJobFeedViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        WorkerJobFeedScreen(state, viewModel::refresh, viewModel::retry, viewModel::accept, viewModel::reject,
            { job -> navigation.openJob(job.bookingId, job.visitIndex) })
    }
    navigation<WorkerJobDetailsRoute> { route ->
        val navigation = LocalWorkerNavigation.current
        val viewModel = koinViewModel<WorkerJobDetailsViewModel> { parametersOf(route.bookingId, route.visitIndex) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        WorkerJobDetailsScreen(state, navigation.back, viewModel::retry, viewModel::requestArrival,
            viewModel::start, viewModel::complete, viewModel::publishLocation, viewModel::locationPermissionDenied)
    }
    navigation<WorkerEarningsRoute> {
        val viewModel = koinViewModel<WorkerEarningsViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        WorkerEarningsScreen(state, viewModel::retry)
    }
}

val appModules = listOf(storageModule, networkModule, repositoryModule, viewModelModule, navigationModule)
