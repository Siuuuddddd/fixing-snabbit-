package com.snabbit.prototype.mobile.data.booking.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class MatchAndHoldDto(
    val customerId: String,
    val serviceId: String,
    val bookingType: String,
    val durationHours: Long,
    val addressId: String,
    val startsAt: List<String> = emptyList(),
    val visits: List<RequestedVisitDto> = emptyList(),
)

@Serializable data class RequestedVisitDto(
    val date: String,
    val startTime: String,
    val durationHours: Long,
)

@Serializable data class VisitAvailabilityDto(
    val startsAt: String,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long? = null,
    val durationMinutes: Int = 0,
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
    val workerId: String? = null,
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
    val durationMinutes: Int = 0,
    val arrivalDueAt: String? = null,
    val selectedWorkerName: String? = null,
    val selectedWorkerTier: String? = null,
    val selectedWorkerRating: Double? = null,
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
    val serverNow: String? = null,
    val maskedHomeContactPhone: String? = null,
    val cancellationType: String? = null,
    val cancellationReason: String? = null,
    val cancellationFeeMinor: Long? = null,
    val refundAmountMinor: Long? = null,
    val cancelledAt: String? = null,
)

@Serializable data class WorkerOptionVisitDto(val visitIndex: Int, val priceMinor: Long)
@Serializable data class WorkerOptionDto(
    val workerId: String, val firstName: String, val tier: String, val averageRating: Double,
    val ratingCount: Int, val distanceKm: Double, val tierSurchargeMinor: Long,
    val visits: List<WorkerOptionVisitDto>,
)
@Serializable data class WorkerOptionsDto(
    val bookingId: String, val visitIndex: Int? = null,
    val favourites: List<WorkerOptionDto>, val others: List<WorkerOptionDto>,
)
@Serializable data class WorkerSelectionDto(val visitIndex: Int, val workerId: String)
@Serializable data class HomeContactRequestDto(val mode: String, val name: String? = null, val phone: String? = null)
@Serializable data class HomeContactDto(val name: String, val maskedPhone: String, val isBooker: Boolean)
@Serializable data class VerifyArrivalOtpDto(val code: String)
@Serializable data class ArrivalOtpStatusDto(
    val status: String, val remainingSends: Int? = null, val retryAfterSeconds: Long? = null,
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

    @GET("api/v1/bookings/{bookingId}/worker-options")
    suspend fun workerOptions(
        @Path("bookingId") bookingId: String,
        @retrofit2.http.Query("visitIndex") visitIndex: Int,
    ): WorkerOptionsDto

    @PUT("api/v1/bookings/{bookingId}/worker-selection")
    suspend fun selectWorker(@Path("bookingId") bookingId: String, @Body request: WorkerSelectionDto): BookingDto

    @PUT("api/v1/bookings/{bookingId}/home-contact")
    suspend fun updateHomeContact(@Path("bookingId") bookingId: String, @Body request: HomeContactRequestDto): HomeContactDto

    @POST("api/v1/bookings/{bookingId}/visits/{visitIndex}/arrival-otp/verify")
    suspend fun verifyArrivalOtp(
        @Path("bookingId") bookingId: String,
        @Path("visitIndex") visitIndex: Int,
        @Body request: VerifyArrivalOtpDto,
    ): ArrivalOtpStatusDto

    @POST("api/v1/bookings/{bookingId}/visits/{visitIndex}/arrival-otp/resend")
    suspend fun resendArrivalOtp(
        @Path("bookingId") bookingId: String,
        @Path("visitIndex") visitIndex: Int,
    ): ArrivalOtpStatusDto

    @POST("api/v1/bookings/{bookingId}/payments")
    suspend fun startPayment(
        @Path("bookingId") bookingId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: StartPaymentDto,
    ): BookingPaymentDto
}
