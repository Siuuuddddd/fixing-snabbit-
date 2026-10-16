package com.snabbit.prototype.mobile.domain.booking

data class BookingDraft(
    val serviceId: String,
    val addressId: String,
    val bookingType: BookingType,
    val durationHours: Int,
    val startsAt: List<String>,
    val visitDurationHours: List<Int> = emptyList(),
) {
    val resolvedVisitDurationHours: List<Int>
        get() = visitDurationHours.takeIf { it.size == startsAt.size }
            ?: List(startsAt.size) { durationHours }
}

enum class BookingType { INSTANT, SCHEDULED, MULTI_DAY }

data class VisitAvailability(
    val startsAt: String,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long?,
    val durationMinutes: Int = 0,
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
    val cancellationType: String? = null,
    val cancellationReason: String? = null,
    val cancellationFeeMinor: Long? = null,
    val refundAmountMinor: Long? = null,
    val serverNow: String? = null,
    val maskedHomeContactPhone: String? = null,
)

data class BookingVisit(
    val visitIndex: Int,
    val workerId: String?,
    val startsAt: String,
    val endsAt: String,
    val offerStatus: String,
    val jobStatus: String?,
    val durationMinutes: Int = 0,
    val arrivalDueAt: String? = null,
    val selectedWorkerName: String? = null,
    val selectedWorkerTier: String? = null,
    val selectedWorkerRating: Double? = null,
)

data class WorkerOption(
    val workerId: String, val firstName: String, val tier: String, val rating: Double,
    val ratingCount: Int, val distanceKm: Double, val tierSurchargeMinor: Long,
    val priceMinor: Long, val isFavourite: Boolean,
)
data class WorkerOptions(val visitIndex: Int, val favourites: List<WorkerOption>, val others: List<WorkerOption>)
data class HomeContact(val name: String, val maskedPhone: String, val isBooker: Boolean)
data class ArrivalOtpStatus(val status: String, val remainingSends: Int?, val retryAfterSeconds: Long?)

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
    suspend fun workerOptions(bookingId: String, visitIndex: Int): WorkerOptions = error("Worker options are not supported")
    suspend fun selectWorker(bookingId: String, visitIndex: Int, workerId: String): Booking = error("Worker selection is not supported")
    suspend fun updateHomeContact(bookingId: String, name: String?, phone: String?): HomeContact = error("Home contact is not supported")
    suspend fun verifyArrivalOtp(bookingId: String, visitIndex: Int, code: String): ArrivalOtpStatus = error("Arrival OTP is not supported")
    suspend fun resendArrivalOtp(bookingId: String, visitIndex: Int): ArrivalOtpStatus = error("Arrival OTP is not supported")
    suspend fun startPayment(
        bookingId: String,
        walletId: String,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPayment
}
