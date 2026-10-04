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
            AddressServiceabilityRoute,
            SlotSelectionRoute,
            HouseholdPreferencesRoute,
            BookingSummaryRoute,
            FindingWorkerRoute,
            LiveTrackingRoute,
            BookingCompletedRoute,
            CancelBookingRoute,
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
