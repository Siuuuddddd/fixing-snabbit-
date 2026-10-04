package com.snabbit.prototype.mobile.domain.bookings

import com.snabbit.prototype.mobile.domain.booking.Booking

interface KnownBookingDataSource {
    suspend fun remember(bookingId: String)
    suspend fun bookingIds(): List<String>
}

fun interface BookingListRepository {
    suspend fun bookings(): List<Booking>
}
