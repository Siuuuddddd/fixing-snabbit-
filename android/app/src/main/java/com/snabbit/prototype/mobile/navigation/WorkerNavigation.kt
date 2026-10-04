package com.snabbit.prototype.mobile.navigation

import androidx.compose.runtime.staticCompositionLocalOf

data class WorkerNavigation(
    val openJob: (String, Int) -> Unit,
    val back: () -> Unit,
)

val LocalWorkerNavigation = staticCompositionLocalOf<WorkerNavigation> {
    error("Worker navigation is unavailable outside the worker shell")
}
