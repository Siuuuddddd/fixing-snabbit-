package com.snabbit.prototype.mobile.domain.booking

data class BookingDraft(
    val serviceId: String,
    val addressId: String,
    val bookingType: BookingType,
    val durationHours: Int,
    val startsAt: List<String>,
)

enum class BookingType { INSTANT, SCHEDULED, MULTI_DAY }

data class VisitAvailability(
    val startsAt: String,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long?,
)

data class BookingAvailability(
    val durationMinutes: Int,
    val allVisitsAvailable: Boolean,
    val sameWorkerAvailableForAllVisits: Boolean,
    val visits: List<VisitAvailability>,
)

data class Booking(
    val id: String,
    val status: BookingStatus,
    val quotedTotalMinor: Long,
    val grossTotalMinor: Long,
    val plusDiscountMinor: Long,
    val visits: List<BookingVisit>,
)

data class BookingVisit(
    val startsAt: String,
    val endsAt: String,
    val offerStatus: String,
)

enum class BookingStatus {
    AWAITING_WORKER, AWAITING_PAYMENT, CONFIRMED, WORKER_UNAVAILABLE,
    EXPIRED, CANCELLED, COMPLETED, UNKNOWN,
}

data class BookingPayment(
    val id: String,
    val totalAmountMinor: Long,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

interface BookingRepository {
    suspend fun availability(draft: BookingDraft): BookingAvailability
    suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String): Booking
    suspend fun booking(bookingId: String): Booking
    suspend fun cancelHold(bookingId: String): Booking
    suspend fun startPayment(
        bookingId: String,
        walletId: String,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPayment
}
