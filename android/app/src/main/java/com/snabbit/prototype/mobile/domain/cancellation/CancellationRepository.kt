package com.snabbit.prototype.mobile.domain.cancellation

import com.snabbit.prototype.mobile.domain.booking.Booking

data class CancellationQuote(
    val bookingId: String,
    val plusActive: Boolean,
    val cancellationFeeMinor: Long,
    val refundAmountMinor: Long,
    val policyBand: CancellationPolicyBand,
    val calculatedAt: String,
)

enum class CancellationPolicyBand { EARLY, LATE, UNKNOWN }

interface CancellationRepository {
    suspend fun quote(bookingId: String): CancellationQuote
    suspend fun cancel(bookingId: String, reason: String?): Booking
}
