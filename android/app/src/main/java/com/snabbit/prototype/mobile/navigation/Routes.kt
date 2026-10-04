package com.snabbit.prototype.mobile.navigation

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.snabbit.prototype.mobile.R
import kotlinx.serialization.Serializable

sealed interface SnabbitRoute : NavKey

@Serializable data object SplashRoute : SnabbitRoute
@Serializable data object OnboardingRoute : SnabbitRoute
@Serializable data object LoginOtpRoute : SnabbitRoute
@Serializable data object HomeRoute : SnabbitRoute
@Serializable data class ServiceDetailsRoute(val serviceId: String) : SnabbitRoute
@Serializable data object AddressServiceabilityRoute : SnabbitRoute
@Serializable data object SlotSelectionRoute : SnabbitRoute
@Serializable data object HouseholdPreferencesRoute : SnabbitRoute
@Serializable data object BookingSummaryRoute : SnabbitRoute
@Serializable data object FindingWorkerRoute : SnabbitRoute
@Serializable data object LiveTrackingRoute : SnabbitRoute
@Serializable data object BookingCompletedRoute : SnabbitRoute
@Serializable data object CancelBookingRoute : SnabbitRoute
@Serializable data object AddMoneyRoute : SnabbitRoute
@Serializable data object SnabbitPlusRoute : SnabbitRoute
@Serializable data object MyBookingsRoute : SnabbitRoute
@Serializable data object NotificationsRoute : SnabbitRoute
@Serializable data object ProfileRoute : SnabbitRoute
@Serializable data object EmptyErrorOfflineRoute : SnabbitRoute
@Serializable data object WorkerLandingRoute : SnabbitRoute
@Serializable data object WorkerJobFeedRoute : SnabbitRoute
@Serializable data object WorkerJobDetailsRoute : SnabbitRoute
@Serializable data object WorkerEarningsRoute : SnabbitRoute

@StringRes
fun SnabbitRoute.titleRes(): Int = when (this) {
    SplashRoute -> R.string.screen_splash
    OnboardingRoute -> R.string.screen_onboarding
    LoginOtpRoute -> R.string.screen_login_otp
    HomeRoute -> R.string.screen_home
    is ServiceDetailsRoute -> R.string.screen_service_details
    AddressServiceabilityRoute -> R.string.screen_address_serviceability
    SlotSelectionRoute -> R.string.screen_slot_selection
    HouseholdPreferencesRoute -> R.string.screen_household_preferences
    BookingSummaryRoute -> R.string.screen_booking_summary
    FindingWorkerRoute -> R.string.screen_finding_worker
    LiveTrackingRoute -> R.string.screen_live_tracking
    BookingCompletedRoute -> R.string.screen_booking_completed
    CancelBookingRoute -> R.string.screen_cancel_booking
    AddMoneyRoute -> R.string.screen_add_money
    SnabbitPlusRoute -> R.string.screen_snabbit_plus
    MyBookingsRoute -> R.string.screen_my_bookings
    NotificationsRoute -> R.string.screen_notifications
    ProfileRoute -> R.string.screen_profile
    EmptyErrorOfflineRoute -> R.string.screen_empty_error_offline
    WorkerLandingRoute -> R.string.screen_worker_landing
    WorkerJobFeedRoute -> R.string.screen_worker_job_feed
    WorkerJobDetailsRoute -> R.string.screen_worker_job_details
    WorkerEarningsRoute -> R.string.screen_worker_earnings
}
