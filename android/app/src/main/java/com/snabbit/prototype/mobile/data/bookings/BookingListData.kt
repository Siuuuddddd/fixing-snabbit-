package com.snabbit.prototype.mobile.data.bookings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.bookings.BookingListRepository
import com.snabbit.prototype.mobile.domain.bookings.KnownBookingDataSource
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Remembers bookings created on this install until the backend exposes customer booking history. */
class DataStoreKnownBookingDataSource(
    private val dataStore: DataStore<Preferences>,
    private val identity: SessionIdentityProvider,
) : KnownBookingDataSource {
    override suspend fun remember(bookingId: String) {
        dataStore.edit { preferences ->
            val key = bookingIdsKey()
            val current = preferences[key].orEmpty().split(',').filter(String::isNotBlank)
            preferences[key] = (listOf(bookingId) + current).distinct().joinToString(",")
        }
    }

    override suspend fun bookingIds(): List<String> = dataStore.data
        .map { preferences -> preferences[bookingIdsKey()].orEmpty().split(',').filter(String::isNotBlank) }
        .first()

    private fun bookingIdsKey() = stringPreferencesKey(
        "known_booking_ids_${identity.customerId() ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)}",
    )
}

class DefaultBookingListRepository(
    private val knownBookings: KnownBookingDataSource,
    private val bookingRepository: BookingRepository,
) : BookingListRepository {
    override suspend fun bookings() = knownBookings.bookingIds().mapNotNull { bookingId ->
        try {
            bookingRepository.booking(bookingId)
        } catch (failure: DataFailure) {
            if (failure.reason == DataFailureReason.NOT_FOUND) null else throw failure
        }
    }
}
