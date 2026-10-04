package com.snabbit.prototype.mobile.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.notification.NotificationItem
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.notification.NotificationRefreshEvents
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationFilter { ALL, BOOKINGS, PAYMENTS, OFFERS }
enum class PushRegistrationState { CHECKING, REGISTERED, NOT_CONFIGURED, FAILED }

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    val filter: NotificationFilter = NotificationFilter.ALL,
    val pushState: PushRegistrationState = PushRegistrationState.CHECKING,
    val error: UiDataError? = null,
) {
    val visibleNotifications: List<NotificationItem> get() = notifications.filter { item ->
        when (filter) {
            NotificationFilter.ALL -> true
            NotificationFilter.BOOKINGS -> item.eventType.contains("BOOKING", ignoreCase = true) || item.eventType.contains("VISIT", ignoreCase = true)
            NotificationFilter.PAYMENTS -> item.eventType.contains("PAYMENT", ignoreCase = true) || item.eventType.contains("WALLET", ignoreCase = true) || item.eventType.contains("PLUS", ignoreCase = true)
            NotificationFilter.OFFERS -> item.eventType.contains("OFFER", ignoreCase = true) || item.eventType.contains("PROMO", ignoreCase = true)
        }
    }
}

class NotificationsViewModel(
    private val repository: NotificationRepository,
    refreshEvents: NotificationRefreshEvents,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load(false)
        viewModelScope.launch(dispatcher) { refreshEvents.events.collect { load(false) } }
    }
    fun refresh() = load(true)
    fun retry() = load(false)
    fun selectFilter(filter: NotificationFilter) = _uiState.update { it.copy(filter = filter) }

    fun markRead(id: Long) = viewModelScope.launch(dispatcher) {
        val current = _uiState.value.notifications.firstOrNull { it.id == id } ?: return@launch
        if (current.readAt != null) return@launch
        runCatching { repository.markRead(id) }
            .onSuccess { updated -> _uiState.update { state -> state.copy(notifications = state.notifications.map { if (it.id == id) updated else it }) } }
            .onFailure { failure -> _uiState.update { it.copy(error = failure.toUiDataError()) } }
    }

    fun retryPushRegistration() = viewModelScope.launch(dispatcher) { syncPush() }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.notifications.isEmpty(), isRefreshing = refreshing, error = null) }
        val push = async { syncPush() }
        runCatching { loadHistory() }
            .onSuccess { notifications -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, notifications = notifications) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
        push.await()
    }

    private suspend fun loadHistory(): List<NotificationItem> {
        val all = mutableListOf<NotificationItem>()
        var cursor = 0L
        while (true) {
            val page = repository.history(cursor)
            all += page
            if (page.size < 100) return all.distinctBy { it.id }.sortedByDescending { it.id }
            val nextCursor = page.maxOf { it.id }
            if (nextCursor <= cursor) return all.distinctBy { it.id }.sortedByDescending { it.id }
            cursor = nextCursor
        }
    }

    private suspend fun syncPush() {
        _uiState.update { it.copy(pushState = PushRegistrationState.CHECKING) }
        runCatching { repository.registerCurrentDevice() }
            .onSuccess { registration ->
                _uiState.update { it.copy(pushState = if (registration == null) PushRegistrationState.NOT_CONFIGURED else PushRegistrationState.REGISTERED) }
            }
            .onFailure { _uiState.update { it.copy(pushState = PushRegistrationState.FAILED) } }
    }
}
