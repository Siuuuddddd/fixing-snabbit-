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
import com.snabbit.prototype.mobile.data.address.AndroidDeviceLocationDataSource
import com.snabbit.prototype.mobile.data.address.DefaultAddressRepository
import com.snabbit.prototype.mobile.data.address.remote.AddressApi
import com.snabbit.prototype.mobile.data.address.remote.AddressRemoteDataSource
import com.snabbit.prototype.mobile.data.address.remote.RetrofitAddressRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.DefaultCatalogRepository
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogApi
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.remote.RetrofitCatalogRemoteDataSource
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
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
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
import com.snabbit.prototype.mobile.navigation.SlotSelectionRoute
import com.snabbit.prototype.mobile.navigation.SnabbitPlusRoute
import com.snabbit.prototype.mobile.navigation.WorkerEarningsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobDetailsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobFeedRoute
import com.snabbit.prototype.mobile.navigation.WorkerLandingRoute
import com.snabbit.prototype.mobile.navigation.titleRes
import com.snabbit.prototype.mobile.navigation.LocalCustomerNavigation
import com.snabbit.prototype.mobile.ui.address.AddressScreen
import com.snabbit.prototype.mobile.ui.address.AddressViewModel
import com.snabbit.prototype.mobile.ui.home.HomeScreen
import com.snabbit.prototype.mobile.ui.home.HomeViewModel
import com.snabbit.prototype.mobile.ui.login.LoginViewModel
import com.snabbit.prototype.mobile.ui.onboarding.OnboardingViewModel
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderScreen
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderViewModel
import com.snabbit.prototype.mobile.ui.splash.SplashViewModel
import com.snabbit.prototype.mobile.ui.service.ServiceDetailsScreen
import com.snabbit.prototype.mobile.ui.service.ServiceDetailsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val storageModule = module {
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
}

private val repositoryModule = module {
    single { AppRole.fromBuild() }
    single<AuthRepository> { DefaultAuthRepository(get(), get(), get(), get()) }
    single<LaunchRepository> { DefaultLaunchRepository(get(), get()) }
    single<SessionIdentityProvider> { JwtSessionIdentityProvider(get(), get()) }
    single<CatalogRepository> { DefaultCatalogRepository(get(), get()) }
    single<AddressRepository> { DefaultAddressRepository(get(), get(), get()) }
    single<DeviceLocationDataSource> { AndroidDeviceLocationDataSource(androidContext()) }
}

private val viewModelModule = module {
    viewModel { PlaceholderViewModel() }
    viewModel { SplashViewModel(get()) }
    viewModel { OnboardingViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { (serviceId: String) -> ServiceDetailsViewModel(serviceId, get()) }
    viewModel { AddressViewModel(get(), get()) }
}

@OptIn(KoinExperimentalAPI::class)
private val navigationModule = module {
    navigation<HomeRoute> {
        val navigation = LocalCustomerNavigation.current
        val viewModel = koinViewModel<HomeViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onServiceSelected = navigation.openService,
            onAddressesSelected = navigation.openAddresses,
            onNotificationsSelected = navigation.openNotifications,
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
            onContinue = navigation.openAddresses,
        )
    }
    navigation<AddressServiceabilityRoute> {
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
        )
    }
    navigation<SlotSelectionRoute> { PlaceholderScreen(SlotSelectionRoute.titleRes()) }
    navigation<HouseholdPreferencesRoute> { PlaceholderScreen(HouseholdPreferencesRoute.titleRes()) }
    navigation<BookingSummaryRoute> { PlaceholderScreen(BookingSummaryRoute.titleRes()) }
    navigation<FindingWorkerRoute> { PlaceholderScreen(FindingWorkerRoute.titleRes()) }
    navigation<LiveTrackingRoute> { PlaceholderScreen(LiveTrackingRoute.titleRes()) }
    navigation<BookingCompletedRoute> { PlaceholderScreen(BookingCompletedRoute.titleRes()) }
    navigation<CancelBookingRoute> { PlaceholderScreen(CancelBookingRoute.titleRes()) }
    navigation<AddMoneyRoute> { PlaceholderScreen(AddMoneyRoute.titleRes()) }
    navigation<SnabbitPlusRoute> { PlaceholderScreen(SnabbitPlusRoute.titleRes()) }
    navigation<MyBookingsRoute> { PlaceholderScreen(MyBookingsRoute.titleRes()) }
    navigation<NotificationsRoute> { PlaceholderScreen(NotificationsRoute.titleRes()) }
    navigation<ProfileRoute> { PlaceholderScreen(ProfileRoute.titleRes()) }
    navigation<EmptyErrorOfflineRoute> { PlaceholderScreen(EmptyErrorOfflineRoute.titleRes()) }
    navigation<WorkerLandingRoute> { PlaceholderScreen(WorkerLandingRoute.titleRes()) }
    navigation<WorkerJobFeedRoute> { PlaceholderScreen(WorkerJobFeedRoute.titleRes()) }
    navigation<WorkerJobDetailsRoute> { PlaceholderScreen(WorkerJobDetailsRoute.titleRes()) }
    navigation<WorkerEarningsRoute> { PlaceholderScreen(WorkerEarningsRoute.titleRes()) }
}

val appModules = listOf(storageModule, networkModule, repositoryModule, viewModelModule, navigationModule)
