package com.snabbit.prototype.mobile.navigation

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingType
import java.time.Instant
import kotlinx.serialization.Serializable

sealed interface SnabbitRoute : NavKey

@Serializable data object SplashRoute : SnabbitRoute
@Serializable data object OnboardingRoute : SnabbitRoute
@Serializable data object LoginOtpRoute : SnabbitRoute
@Serializable data object LoginRoute : SnabbitRoute
@Serializable data object WorkerPickerRoute : SnabbitRoute
@Serializable data object HomeRoute : SnabbitRoute
@Serializable data class ServiceDetailsRoute(val serviceId: String) : SnabbitRoute
@Serializable data class AddressServiceabilityRoute(val serviceId: String? = null, val durationHours: Int? = null) : SnabbitRoute
@Serializable data class ScheduleRoute(val serviceId: String, val addressId: String) : SnabbitRoute
@Serializable data class ScheduleStartTimeRoute(
    val serviceId: String,
    val addressId: String,
    val dates: List<String>,
    val durationHours: Int,
) : SnabbitRoute
@Serializable data class HouseholdPreferencesRoute(
    val serviceId: String, val addressId: String, val durationHours: Int,
    val bookingType: String, val startsAt: List<String>,
) : SnabbitRoute
@Serializable data class BookingSummaryRoute(
    val serviceId: String = "", val addressId: String = "", val durationHours: Int = 0,
    val bookingType: String = "", val startsAt: List<String> = emptyList(),
) : SnabbitRoute
@Serializable data class FindingWorkerRoute(val bookingId: String, val walletId: String?, val useWallet: Boolean) : SnabbitRoute
@Serializable data class LiveTrackingRoute(val bookingId: String, val visitIndex: Int) : SnabbitRoute
@Serializable data class BookingCompletedRoute(val bookingId: String, val visitIndex: Int, val workerId: String) : SnabbitRoute
@Serializable data class CancelBookingRoute(val bookingId: String) : SnabbitRoute
@Serializable data object AddMoneyRoute : SnabbitRoute
@Serializable data object SnabbitPlusRoute : SnabbitRoute
@Serializable data object MyBookingsRoute : SnabbitRoute
@Serializable data object NotificationsRoute : SnabbitRoute
@Serializable data object ProfileRoute : SnabbitRoute
@Serializable data object EmptyErrorOfflineRoute : SnabbitRoute
@Serializable data object WorkerLandingRoute : SnabbitRoute
@Serializable data object WorkerJobFeedRoute : SnabbitRoute
@Serializable data class WorkerJobDetailsRoute(val bookingId: String, val visitIndex: Int) : SnabbitRoute
@Serializable data object WorkerEarningsRoute : SnabbitRoute

internal fun BookingSummaryRoute.toBookingDraftOrNull(): BookingDraft? {
    val type = BookingType.entries.firstOrNull { it.name == bookingType } ?: return null
    if (serviceId.isBlank() || addressId.isBlank() || durationHours <= 0 || startsAt.isEmpty()) return null
    if (startsAt.any { runCatching { Instant.parse(it) }.isFailure }) return null
    return BookingDraft(serviceId, addressId, type, durationHours, startsAt)
}

@StringRes
fun SnabbitRoute.titleRes(): Int = when (this) {
    SplashRoute -> R.string.screen_splash
    OnboardingRoute -> R.string.screen_onboarding
    LoginOtpRoute -> R.string.screen_login_otp
    LoginRoute -> R.string.screen_login
    WorkerPickerRoute -> R.string.screen_worker_picker
    HomeRoute -> R.string.screen_home
    is ServiceDetailsRoute -> R.string.screen_service_details
    is AddressServiceabilityRoute -> R.string.screen_address_serviceability
    is ScheduleRoute -> R.string.schedule_title
    is ScheduleStartTimeRoute -> R.string.schedule_title
    is HouseholdPreferencesRoute -> R.string.screen_household_preferences
    is BookingSummaryRoute -> R.string.screen_booking_summary
    is FindingWorkerRoute -> R.string.screen_finding_worker
    is LiveTrackingRoute -> R.string.screen_live_tracking
    is BookingCompletedRoute -> R.string.screen_booking_completed
    is CancelBookingRoute -> R.string.screen_cancel_booking
    AddMoneyRoute -> R.string.screen_add_money
    SnabbitPlusRoute -> R.string.screen_snabbit_plus
    MyBookingsRoute -> R.string.screen_my_bookings
    NotificationsRoute -> R.string.screen_notifications
    ProfileRoute -> R.string.screen_profile
    EmptyErrorOfflineRoute -> R.string.screen_empty_error_offline
    WorkerLandingRoute -> R.string.screen_worker_landing
    WorkerJobFeedRoute -> R.string.screen_worker_job_feed
    is WorkerJobDetailsRoute -> R.string.screen_worker_job_details
    WorkerEarningsRoute -> R.string.screen_worker_earnings
}
