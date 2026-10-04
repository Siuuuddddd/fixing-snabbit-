package com.snabbit.prototype.mobile.data.cancellation

import com.snabbit.prototype.mobile.data.booking.remote.BookingDto
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.BookingVisit
import com.snabbit.prototype.mobile.domain.cancellation.CancellationPolicyBand
import com.snabbit.prototype.mobile.domain.cancellation.CancellationQuote
import com.snabbit.prototype.mobile.domain.cancellation.CancellationRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable data class CancellationQuoteDto(
    val bookingId: String,
    val plusActive: Boolean,
    val cancellationFeeMinor: Long,
    val refundAmountMinor: Long,
    val policyBand: String,
    val calculatedAt: String,
)

@Serializable data class CancelBookingDto(val reason: String? = null)

interface CancellationApi {
    @GET("api/v1/bookings/{bookingId}/cancellation-quote")
    suspend fun quote(@Path("bookingId") bookingId: String): CancellationQuoteDto

    @POST("api/v1/bookings/{bookingId}/cancellations")
    suspend fun cancel(@Path("bookingId") bookingId: String, @Body request: CancelBookingDto): BookingDto
}

class DefaultCancellationRepository(
    private val api: CancellationApi,
    private val json: Json,
) : CancellationRepository {
    override suspend fun quote(bookingId: String) = apiCall(json) {
        api.quote(bookingId).let {
            CancellationQuote(
                it.bookingId, it.plusActive, it.cancellationFeeMinor, it.refundAmountMinor,
                runCatching { CancellationPolicyBand.valueOf(it.policyBand) }.getOrDefault(CancellationPolicyBand.UNKNOWN),
                it.calculatedAt,
            )
        }
    }

    override suspend fun cancel(bookingId: String, reason: String?) = apiCall(json) {
        api.cancel(bookingId, CancelBookingDto(reason?.trim()?.ifBlank { null })).toDomain()
    }

    private fun BookingDto.toDomain() = Booking(
        bookingId,
        runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.UNKNOWN),
        quotedTotalMinor,
        grossTotalMinor,
        plusDiscountMinor,
        visits.map { BookingVisit(it.visitIndex, it.workerId, it.startsAt, it.endsAt, it.offerStatus, it.jobStatus) },
        cancellationType,
        cancellationReason,
        cancellationFeeMinor,
        refundAmountMinor,
    )
}
