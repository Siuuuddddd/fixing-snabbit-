package com.snabbit.prototype.mobile.domain.tracking

import kotlinx.coroutines.flow.Flow

data class WorkerLocation(
    val locationId: String,
    val bookingId: String,
    val visitIndex: Int,
    val workerId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: String,
    val receivedAt: String,
)

interface TrackingRepository {
    suspend fun latest(bookingId: String, visitIndex: Int): WorkerLocation
    fun stream(bookingId: String, visitIndex: Int, lastEventId: String?): Flow<WorkerLocation>
}
