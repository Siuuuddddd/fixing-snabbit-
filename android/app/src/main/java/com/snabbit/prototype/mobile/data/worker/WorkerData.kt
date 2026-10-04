package com.snabbit.prototype.mobile.data.worker

import com.snabbit.prototype.mobile.data.booking.remote.BookingDto
import com.snabbit.prototype.mobile.data.booking.remote.BookingRequirementsDto
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.address.LocationSample
import com.snabbit.prototype.mobile.domain.worker.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.*

@Serializable data class WorkerOfferDto(
    val offerId: String, val bookingId: String, val visitIndex: Int, val serviceId: String,
    val serviceCode: String, val serviceName: String, val startsAt: String, val endsAt: String,
    val durationMinutes: Int, val offerExpiresAt: String, val requirements: BookingRequirementsDto,
)
@Serializable data class WorkerJobDto(
    val bookingId: String, val visitIndex: Int, val serviceId: String, val serviceCode: String,
    val serviceName: String, val startsAt: String, val endsAt: String, val durationMinutes: Int,
    val jobStatus: String, val requirements: BookingRequirementsDto,
    val serviceLatitude: Double? = null, val serviceLongitude: Double? = null,
    val serviceAddressLabel: String? = null, val serviceAddressLine: String? = null,
    val serviceAddressPincode: String? = null,
)
@Serializable data class WorkerArrivalDto(val latitude: Double, val longitude: Double, val accuracyMeters: Double)
@Serializable data class WorkerAvailabilityCommandDto(
    val status: String, val availableFrom: String? = null, val availableUntil: String? = null,
)
@Serializable data class WorkerAvailabilityDto(
    val serviceDate: String, val availableFrom: String, val availableUntil: String, val status: String,
)
@Serializable data class WorkerLocationCommandDto(
    val latitude: Double, val longitude: Double, val accuracyMeters: Double, val capturedAt: String,
)
@Serializable data class WorkerLocationDto(
    val locationId: String, val workerId: String, val latitude: Double, val longitude: Double,
    val accuracyMeters: Double, val capturedAt: String, val receivedAt: String,
)

interface WorkerApi {
    @GET("api/v1/worker/bookings/offers") suspend fun offers(): List<WorkerOfferDto>
    @GET("api/v1/worker/bookings/jobs") suspend fun jobs(): List<WorkerJobDto>
    @POST("api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept")
    suspend fun accept(@Path("bookingId") bookingId: String, @Path("offerId") offerId: String): BookingDto
    @POST("api/v1/worker/bookings/{bookingId}/offers/{offerId}/reject")
    suspend fun reject(@Path("bookingId") bookingId: String, @Path("offerId") offerId: String): BookingDto
    @POST("api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive")
    suspend fun arrive(@Path("bookingId") bookingId: String, @Path("visitIndex") visitIndex: Int, @Body body: WorkerArrivalDto): BookingDto
    @POST("api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start")
    suspend fun start(@Path("bookingId") bookingId: String, @Path("visitIndex") visitIndex: Int): BookingDto
    @POST("api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete")
    suspend fun complete(@Path("bookingId") bookingId: String, @Path("visitIndex") visitIndex: Int): BookingDto
    @GET("api/v1/workers/me/availability")
    suspend fun availability(@Query("from") from: String, @Query("to") to: String): List<WorkerAvailabilityDto>
    @PUT("api/v1/workers/me/availability/{serviceDate}")
    suspend fun updateAvailability(@Path("serviceDate") date: String, @Body body: WorkerAvailabilityCommandDto): WorkerAvailabilityDto
    @POST("api/v1/workers/me/location")
    suspend fun publishLocation(@Body body: WorkerLocationCommandDto): WorkerLocationDto
}

class DefaultWorkerJobsRepository(private val api: WorkerApi, private val json: Json) : WorkerJobsRepository {
    override suspend fun offers() = apiCall(json) { api.offers() }.map { it.toDomain() }
    override suspend fun jobs() = apiCall(json) { api.jobs() }.map { it.toDomain() }
    override suspend fun accept(bookingId: String, offerId: String) { apiCall(json) { api.accept(bookingId, offerId) } }
    override suspend fun reject(bookingId: String, offerId: String) { apiCall(json) { api.reject(bookingId, offerId) } }
    override suspend fun arrive(bookingId: String, visitIndex: Int, location: LocationSample) {
        apiCall(json) { api.arrive(bookingId, visitIndex, WorkerArrivalDto(location.latitude, location.longitude, location.accuracyMeters)) }
    }
    override suspend fun start(bookingId: String, visitIndex: Int) { apiCall(json) { api.start(bookingId, visitIndex) } }
    override suspend fun complete(bookingId: String, visitIndex: Int) { apiCall(json) { api.complete(bookingId, visitIndex) } }
}

class DefaultWorkerAvailabilityRepository(private val api: WorkerApi, private val json: Json) : WorkerAvailabilityRepository {
    override suspend fun availability(from: String, to: String) = apiCall(json) { api.availability(from, to) }.map { it.toDomain() }
    override suspend fun update(date: String, status: WorkerAvailabilityStatus): WorkerAvailability {
        val command = if (status == WorkerAvailabilityStatus.AVAILABLE)
            WorkerAvailabilityCommandDto(status.name, "08:00:00", "19:00:00")
        else WorkerAvailabilityCommandDto(status.name)
        return apiCall(json) { api.updateAvailability(date, command) }.toDomain()
    }
}

class DefaultWorkerLocationRepository(private val api: WorkerApi, private val json: Json) : WorkerLocationRepository {
    override suspend fun publish(location: LocationSample) {
        apiCall(json) { api.publishLocation(WorkerLocationCommandDto(location.latitude, location.longitude, location.accuracyMeters, location.capturedAtIso)) }
    }
}

private fun BookingRequirementsDto.toDomain() = WorkerRequirements(
    hasPets, petDetails, foodContext, utensilPreferences, preferredLanguages, allergies,
    religiousFoodRestrictions, accessibilityNeeds, workerConstraints, heavyLiftingRequired,
    disallowedChemicals, requiredEquipment,
)
private fun WorkerOfferDto.toDomain() = WorkerOffer(
    offerId, bookingId, visitIndex, serviceName, startsAt, endsAt, durationMinutes, offerExpiresAt, requirements.toDomain(),
)
private fun WorkerJobDto.toDomain() = WorkerJob(
    bookingId, visitIndex, serviceName, startsAt, endsAt, durationMinutes,
    WorkerJobStatus.valueOf(jobStatus), requirements.toDomain(), serviceAddressLabel, serviceAddressLine, serviceAddressPincode,
)
private fun WorkerAvailabilityDto.toDomain() = WorkerAvailability(
    serviceDate, availableFrom, availableUntil, WorkerAvailabilityStatus.valueOf(status),
)
