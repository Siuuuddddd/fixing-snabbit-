package com.snabbit.prototype.mobile.di

import com.snabbit.prototype.mobile.BuildConfig
import com.snabbit.prototype.mobile.data.auth.EncryptedTokenStore
import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.data.network.AuthInterceptor
import com.snabbit.prototype.mobile.data.network.RefreshingAuthenticator
import com.snabbit.prototype.mobile.data.network.TokenRefresher
import com.snabbit.prototype.mobile.data.network.UnsupportedTokenRefresher
import com.snabbit.prototype.mobile.navigation.AddMoneyRoute
import com.snabbit.prototype.mobile.navigation.AddressServiceabilityRoute
import com.snabbit.prototype.mobile.navigation.BookingCompletedRoute
import com.snabbit.prototype.mobile.navigation.BookingSummaryRoute
import com.snabbit.prototype.mobile.navigation.CancelBookingRoute
import com.snabbit.prototype.mobile.navigation.EmptyErrorOfflineRoute
import com.snabbit.prototype.mobile.navigation.FindingWorkerRoute
import com.snabbit.prototype.mobile.navigation.HomeRoute
import com.snabbit.prototype.mobile.navigation.HouseholdPreferencesRoute
import com.snabbit.prototype.mobile.navigation.LiveTrackingRoute
import com.snabbit.prototype.mobile.navigation.LoginOtpRoute
import com.snabbit.prototype.mobile.navigation.MyBookingsRoute
import com.snabbit.prototype.mobile.navigation.NotificationsRoute
import com.snabbit.prototype.mobile.navigation.OnboardingRoute
import com.snabbit.prototype.mobile.navigation.ProfileRoute
import com.snabbit.prototype.mobile.navigation.ServiceDetailsRoute
import com.snabbit.prototype.mobile.navigation.SlotSelectionRoute
import com.snabbit.prototype.mobile.navigation.SnabbitPlusRoute
import com.snabbit.prototype.mobile.navigation.SplashRoute
import com.snabbit.prototype.mobile.navigation.WorkerEarningsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobDetailsRoute
import com.snabbit.prototype.mobile.navigation.WorkerJobFeedRoute
import com.snabbit.prototype.mobile.navigation.WorkerLandingRoute
import com.snabbit.prototype.mobile.navigation.titleRes
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderScreen
import com.snabbit.prototype.mobile.ui.placeholder.PlaceholderViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.navigation3.navigation
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val storageModule = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
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
}

private val repositoryModule = module {
    // Feature repositories are intentionally added with real screens, not invented in the foundation.
}

private val viewModelModule = module {
    viewModel { PlaceholderViewModel() }
}

@OptIn(KoinExperimentalAPI::class)
private val navigationModule = module {
    navigation<SplashRoute> { PlaceholderScreen(SplashRoute.titleRes()) }
    navigation<OnboardingRoute> { PlaceholderScreen(OnboardingRoute.titleRes()) }
    navigation<LoginOtpRoute> { PlaceholderScreen(LoginOtpRoute.titleRes()) }
    navigation<HomeRoute> { PlaceholderScreen(HomeRoute.titleRes()) }
    navigation<ServiceDetailsRoute> { PlaceholderScreen(ServiceDetailsRoute.titleRes()) }
    navigation<AddressServiceabilityRoute> { PlaceholderScreen(AddressServiceabilityRoute.titleRes()) }
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
