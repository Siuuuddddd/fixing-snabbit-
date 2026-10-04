package com.snabbit.prototype.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun designBoardHasTwentyThreeTypedRoutes() {
        val routes = listOf(
            SplashRoute,
            OnboardingRoute,
            LoginOtpRoute,
            HomeRoute,
            ServiceDetailsRoute("service-id"),
            AddressServiceabilityRoute(),
            SlotSelectionRoute("service-id", "address-id", 2),
            HouseholdPreferencesRoute("service-id", "address-id", 2, "SCHEDULED", listOf("2026-10-05T04:30:00Z")),
            BookingSummaryRoute("service-id", "address-id", 2, "SCHEDULED", listOf("2026-10-05T04:30:00Z")),
            FindingWorkerRoute("booking-id", "wallet-id", true),
            LiveTrackingRoute("booking-id", 0),
            BookingCompletedRoute("booking-id", 0, "worker-id"),
            CancelBookingRoute("booking-id"),
            AddMoneyRoute,
            SnabbitPlusRoute,
            MyBookingsRoute,
            NotificationsRoute,
            ProfileRoute,
            EmptyErrorOfflineRoute,
            WorkerLandingRoute,
            WorkerJobFeedRoute,
            WorkerJobDetailsRoute,
            WorkerEarningsRoute,
        )

        assertEquals(23, routes.distinct().size)
    }
}
