package com.snabbit.prototype.mobile.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PushDestination {
    data class CustomerBooking(val bookingId: String) : PushDestination
    data object CustomerWallet : PushDestination
    data class WorkerJob(val bookingId: String, val visitIndex: Int) : PushDestination
}

object PushDestinationParser {
    fun parse(data: Map<String, String>, role: AppRole): PushDestination? {
        val type = data["type"].orEmpty()
        return when (role) {
            AppRole.WORKER -> {
                val bookingId = data["bookingId"]?.takeIf(String::isNotBlank) ?: return null
                val visitIndex = data["visitIndex"]?.toIntOrNull() ?: 0
                PushDestination.WorkerJob(bookingId, visitIndex)
            }
            AppRole.CUSTOMER -> when {
                type.contains("Wallet", ignoreCase = true) ||
                    type.contains("Payment", ignoreCase = true) ||
                    type.contains("Refund", ignoreCase = true) ||
                    type.contains("Plus", ignoreCase = true) -> PushDestination.CustomerWallet
                else -> data["bookingId"]?.takeIf(String::isNotBlank)
                    ?.let { PushDestination.CustomerBooking(it) }
            }
        }
    }
}

class PushNavigationCoordinator {
    private val mutableDestination = MutableStateFlow<PushDestination?>(null)
    val destination: StateFlow<PushDestination?> = mutableDestination.asStateFlow()

    fun open(data: Map<String, String>, role: AppRole) {
        PushDestinationParser.parse(data, role)?.let { mutableDestination.value = it }
    }

    fun consume(destination: PushDestination) {
        mutableDestination.compareAndSet(destination, null)
    }
}
