package com.snabbit.prototype.mobile.data.booking.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class MatchAndHoldDto(
    val customerId: String,
    val serviceId: String,
    val bookingType: String,
    val durationHours: Long,
    val addressId: String,
    val startsAt: List<String>,
)

@Serializable data class VisitAvailabilityDto(
    val startsAt: String,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long? = null,
)

@Serializable data class BookingAvailabilityDto(
    val serviceId: String,
    val durationMinutes: Int,
    val allVisitsAvailable: Boolean,
    val sameWorkerAvailableForAllVisits: Boolean,
    val visits: List<VisitAvailabilityDto>,
)

@Serializable data class BookingRequirementsDto(
    val hasPets: Boolean = false,
    val petDetails: String? = null,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
)

@Serializable data class BookingVisitDto(
    val visitIndex: Int,
    val workerId: String,
    val startsAt: String,
    val endsAt: String,
    val holdExpiresAt: String,
    val offerId: String,
    val offerStatus: String,
    val offerExpiresAt: String,
    val jobStatus: String? = null,
    val arrivedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
)

@Serializable data class BookingDto(
    val bookingId: String,
    val customerId: String,
    val status: String,
    val quotedTotalMinor: Long,
    val grossTotalMinor: Long,
    val plusDiscountMinor: Long,
    val plusPassId: String? = null,
    val requirements: BookingRequirementsDto,
    val visits: List<BookingVisitDto>,
    val cancellationType: String? = null,
    val cancellationReason: String? = null,
    val cancellationFeeMinor: Long? = null,
    val refundAmountMinor: Long? = null,
    val cancelledAt: String? = null,
)

@Serializable data class StartPaymentDto(val walletId: String, val useWallet: Boolean)

@Serializable data class BookingPaymentDto(
    val paymentId: String,
    val bookingId: String,
    val walletId: String,
    val totalAmountMinor: Long,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val status: String,
    val providerOrderId: String? = null,
    val reservationExpiresAt: String? = null,
)

interface BookingApi {
    @POST("api/v1/bookings/availability")
    suspend fun availability(@Body request: MatchAndHoldDto): BookingAvailabilityDto

    @POST("api/v1/bookings/match-and-hold")
    suspend fun matchAndHold(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: MatchAndHoldDto,
    ): BookingDto

    @GET("api/v1/bookings/{bookingId}")
    suspend fun booking(@Path("bookingId") bookingId: String): BookingDto

    @DELETE("api/v1/bookings/{bookingId}/hold")
    suspend fun cancelHold(@Path("bookingId") bookingId: String): BookingDto

    @POST("api/v1/bookings/{bookingId}/payments")
    suspend fun startPayment(
        @Path("bookingId") bookingId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: StartPaymentDto,
    ): BookingPaymentDto
}
