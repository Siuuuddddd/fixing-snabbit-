package com.snabbit.prototype.mobile.navigation

import androidx.compose.runtime.staticCompositionLocalOf

data class CustomerNavigation(
    val openService: (String) -> Unit,
    val openAddresses: () -> Unit,
    val openNotifications: () -> Unit,
    val back: () -> Unit,
)

val LocalCustomerNavigation = staticCompositionLocalOf<CustomerNavigation> {
    error("Customer navigation is unavailable outside the customer shell")
}
