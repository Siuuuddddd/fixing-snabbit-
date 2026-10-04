package com.snabbit.prototype.mobile.ui.notification

import com.snabbit.prototype.mobile.domain.notification.DeviceRegistration
import com.snabbit.prototype.mobile.domain.notification.NotificationItem
import com.snabbit.prototype.mobile.domain.notification.NotificationRepository
import com.snabbit.prototype.mobile.domain.notification.NotificationRefreshEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @Test fun `loads backend history registers device filters and marks read`() = runTest {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationsViewModel(repository, FakeNotificationRefreshEvents(), StandardTestDispatcher(testScheduler))
        runCurrent()

        assertEquals(PushRegistrationState.REGISTERED, viewModel.uiState.value.pushState)
        assertEquals(2, viewModel.uiState.value.notifications.size)
        viewModel.selectFilter(NotificationFilter.PAYMENTS)
        assertEquals(listOf(2L), viewModel.uiState.value.visibleNotifications.map { it.id })

        viewModel.markRead(2)
        runCurrent()
        assertNotNull(viewModel.uiState.value.notifications.single { it.id == 2L }.readAt)
    }

    @Test fun `missing firebase configuration does not hide history`() = runTest {
        val repository = FakeNotificationRepository(registration = null)
        val viewModel = NotificationsViewModel(repository, FakeNotificationRefreshEvents(), StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(PushRegistrationState.NOT_CONFIGURED, viewModel.uiState.value.pushState)
        assertEquals(2, viewModel.uiState.value.notifications.size)
    }
}

private class FakeNotificationRefreshEvents : NotificationRefreshEvents {
    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val events = updates
    override fun notifyChanged() { updates.tryEmit(Unit) }
}

private class FakeNotificationRepository(
    private val registration: DeviceRegistration? = DeviceRegistration("device", "ANDROID", true, "now"),
) : NotificationRepository {
    private val items = mutableListOf(
        NotificationItem(1, "BOOKING_CONFIRMED", "Confirmed", "Body", false, "now", null),
        NotificationItem(2, "PAYMENT_CAPTURED", "Paid", "Body", true, "now", null),
    )
    override suspend fun history(afterNotificationId: Long) = items.filter { it.id > afterNotificationId }
    override suspend fun markRead(notificationId: Long): NotificationItem {
        val index = items.indexOfFirst { it.id == notificationId }
        return items[index].copy(readAt = "read").also { items[index] = it }
    }
    override suspend fun registerCurrentDevice() = registration
    override suspend fun registerToken(pushToken: String) = requireNotNull(registration)
    override suspend fun unregisterCurrentDevice() = Unit
}
