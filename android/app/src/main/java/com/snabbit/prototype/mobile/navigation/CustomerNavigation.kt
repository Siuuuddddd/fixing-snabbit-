package com.snabbit.prototype.mobile.navigation

import androidx.compose.runtime.staticCompositionLocalOf

data class CustomerNavigation(
    val openService: (String) -> Unit,
    val openAddresses: (String?, Int?) -> Unit,
    val openSlots: (String, String, Int) -> Unit,
    val openHousehold: (String, String, Int, String, List<String>) -> Unit,
    val openSummary: (String, String, Int, String, List<String>) -> Unit,
    val openFindingWorker: (String, String?, Boolean) -> Unit,
    val openTracking: (String, Int) -> Unit,
    val openCompleted: (String, Int, String) -> Unit,
    val openCancellation: (String) -> Unit,
    val returnHome: () -> Unit,
    val openNotifications: () -> Unit,
    val back: () -> Unit,
)

val LocalCustomerNavigation = staticCompositionLocalOf<CustomerNavigation> {
    error("Customer navigation is unavailable outside the customer shell")
}
