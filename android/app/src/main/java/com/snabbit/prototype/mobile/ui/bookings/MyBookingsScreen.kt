package com.snabbit.prototype.mobile.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.common.LoadingStatePanel
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.components.StatusBadge
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.ErrorRed
import com.snabbit.prototype.mobile.ui.theme.InProgressBlue
import com.snabbit.prototype.mobile.ui.theme.PendingAmber

@Composable
fun MyBookingsScreen(state: MyBookingsUiState, onRefresh: () -> Unit, onRetry: () -> Unit, onFilterSelected: (BookingListFilter) -> Unit, modifier: Modifier = Modifier) {
    PullToRefreshBox(state.isRefreshing, onRefresh, modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingStatePanel(stringResource(R.string.bookings_loading), Modifier.align(Alignment.Center))
            state.error != null && state.bookings.isEmpty() -> DataErrorPanel(state.error, onRetry, Modifier.align(Alignment.Center))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
            ) {
                item { Text(stringResource(R.string.bookings_title), style = MaterialTheme.typography.headlineMedium) }
                item {
                    Text(stringResource(R.string.bookings_local_scope_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                        BookingListFilter.entries.forEach { filter ->
                            FilterChip(filter == state.filter, { onFilterSelected(filter) }, { Text(filter.label()) })
                        }
                    }
                }
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                if (state.visibleBookings.isEmpty()) {
                    item { EmptyStatePanel(stringResource(R.string.bookings_empty_title), stringResource(R.string.bookings_empty_body)) }
                } else {
                    items(state.visibleBookings, key = Booking::id) { BookingCard(it) }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: Booking) {
    val firstVisit = booking.visits.minByOrNull { it.visitIndex }
    SnabbitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bookings_booking_number, booking.id.take(8)), style = MaterialTheme.typography.titleMedium)
                firstVisit?.let { Text(it.startsAt, style = MaterialTheme.typography.bodyMedium) }
                Text(pluralStringResource(R.plurals.bookings_visit_count, booking.visits.size, booking.visits.size), style = MaterialTheme.typography.bodySmall)
            }
            StatusBadge(
                stringResource(booking.status.labelResource()),
                when (booking.status) {
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.CONFIRMED,
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.COMPLETED -> ConfirmedGreen
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.CANCELLED,
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.EXPIRED,
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.WORKER_UNAVAILABLE -> ErrorRed
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.AWAITING_WORKER,
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.AWAITING_PAYMENT -> PendingAmber
                    com.snabbit.prototype.mobile.domain.booking.BookingStatus.UNKNOWN -> InProgressBlue
                },
            )
        }
        Text(formatInrMinor(booking.grossTotalMinor), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = SnabbitSpacing.small))
    }
}

private fun BookingStatus.labelResource(): Int = when (this) {
    BookingStatus.AWAITING_WORKER -> R.string.booking_status_awaiting_worker
    BookingStatus.AWAITING_PAYMENT -> R.string.booking_status_awaiting_payment
    BookingStatus.CONFIRMED -> R.string.booking_status_confirmed
    BookingStatus.WORKER_UNAVAILABLE -> R.string.booking_status_worker_unavailable
    BookingStatus.EXPIRED -> R.string.booking_status_expired
    BookingStatus.CANCELLED -> R.string.booking_status_cancelled
    BookingStatus.COMPLETED -> R.string.booking_status_completed
    BookingStatus.UNKNOWN -> R.string.booking_status_unknown
}

@Composable private fun BookingListFilter.label() = stringResource(
    if (this == BookingListFilter.UPCOMING) R.string.bookings_filter_upcoming else R.string.bookings_filter_past,
)
