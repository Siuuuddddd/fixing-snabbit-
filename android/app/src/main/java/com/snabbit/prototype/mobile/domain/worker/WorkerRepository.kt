package com.snabbit.prototype.mobile.domain.worker

import com.snabbit.prototype.mobile.domain.address.LocationSample

enum class WorkerJobStatus { ASSIGNED, ARRIVED, IN_PROGRESS, COMPLETED }
enum class WorkerAvailabilityStatus { AVAILABLE, UNAVAILABLE }

data class WorkerRequirements(
    val hasPets: Boolean,
    val petDetails: String?,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val allergies: List<String>,
    val religiousFoodRestrictions: List<String>,
    val accessibilityNeeds: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
)

data class WorkerOffer(
    val offerId: String,
    val bookingId: String,
    val visitIndex: Int,
    val serviceName: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int,
    val offerExpiresAt: String,
    val requirements: WorkerRequirements,
)

data class WorkerJob(
    val bookingId: String,
    val visitIndex: Int,
    val serviceName: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int,
    val status: WorkerJobStatus,
    val requirements: WorkerRequirements,
    val addressLabel: String?,
    val addressLine: String?,
    val addressPincode: String?,
)

data class WorkerAvailability(
    val serviceDate: String,
    val availableFrom: String,
    val availableUntil: String,
    val status: WorkerAvailabilityStatus,
)

interface WorkerJobsRepository {
    suspend fun offers(): List<WorkerOffer>
    suspend fun jobs(): List<WorkerJob>
    suspend fun accept(bookingId: String, offerId: String)
    suspend fun reject(bookingId: String, offerId: String)
    suspend fun arrive(bookingId: String, visitIndex: Int, location: LocationSample)
    suspend fun start(bookingId: String, visitIndex: Int)
    suspend fun complete(bookingId: String, visitIndex: Int)
}

interface WorkerAvailabilityRepository {
    suspend fun availability(from: String, to: String): List<WorkerAvailability>
    suspend fun update(date: String, status: WorkerAvailabilityStatus): WorkerAvailability
}

interface WorkerLocationRepository {
    suspend fun publish(location: LocationSample)
}
