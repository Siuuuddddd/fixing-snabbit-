package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.VisitSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerLocationResponse
import com.snabbit.prototype.booking.dto.response.WorkerTrackingSnapshot
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.WorkerLocationServiceUnavailable
import com.snabbit.prototype.booking.exception.WorkerTrackingUnavailable
import com.snabbit.prototype.booking.service.BookingLifecycleService
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service

@Service
class WorkerTrackingService(
    private val bookings: BookingLifecycleService,
    private val workerLocations: WorkerLocationClient,
    private val properties: WorkerTrackingProperties,
    private val clock: Clock,
) {
    fun latest(bookingId: UUID, visitIndex: Int, customerId: UUID): WorkerTrackingSnapshot {
        val context = authorize(bookingId, visitIndex, customerId)
        val workerId = assignedWorkerId(context)
        val location = workerLocations.latest(workerId)
            ?: throw WorkerTrackingUnavailable("Worker location is not available yet")
        requireDisplayable(location, workerId)
        return location.toTracking(context)
    }

    fun stream(
        bookingId: UUID,
        visitIndex: Int,
        customerId: UUID,
    ): Flow<ServerSentEvent<WorkerTrackingSnapshot>> {
        authorize(bookingId, visitIndex, customerId)
        return flow {
            var previousLocationId: UUID? = null
            while (true) {
                val (context, location) = try {
                    withContext(Dispatchers.IO) {
                        val current = authorize(bookingId, visitIndex, customerId)
                        current to workerLocations.latest(assignedWorkerId(current))
                    }
                } catch (_: WorkerLocationServiceUnavailable) {
                    withContext(Dispatchers.IO) { authorize(bookingId, visitIndex, customerId) } to null
                }
                if (location != null && isDisplayable(location, assignedWorkerId(context)) &&
                    location.locationId != previousLocationId
                ) {
                    previousLocationId = location.locationId
                    emit(
                        ServerSentEvent.builder(location.toTracking(context))
                            .id(location.locationId.toString())
                            .event("worker-location")
                            .build(),
                    )
                } else {
                    emit(ServerSentEvent.builder<WorkerTrackingSnapshot>().comment("keep-alive").build())
                }
                delay(properties.pollInterval.toMillis())
            }
        }
    }

    private fun authorize(bookingId: UUID, visitIndex: Int, customerId: UUID): TrackingContext {
        val booking = bookings.get(bookingId)
        if (booking.customerId != customerId) throw CustomerAccessDenied()
        val visit = booking.visits.singleOrNull { it.visitIndex == visitIndex }
            ?: throw WorkerTrackingUnavailable("Booking visit does not exist")
        validateTrackingWindow(booking, visit)
        return TrackingContext(bookingId, visit)
    }

    private fun validateTrackingWindow(booking: BookingSnapshot, visit: VisitSnapshot) {
        if (booking.status != "CONFIRMED") {
            throw WorkerTrackingUnavailable("Worker tracking is available only for confirmed bookings")
        }
        if (visit.jobStatus == "COMPLETED") {
            throw WorkerTrackingUnavailable("Worker tracking ended when the visit completed")
        }
        val visibleFrom = visit.startsAt.minus(properties.visibilityLeadTime)
        if (clock.instant().isBefore(visibleFrom)) {
            throw WorkerTrackingUnavailable("Worker tracking opens ${properties.visibilityLeadTime.toHours()} hours before the visit")
        }
    }

    private fun assignedWorkerId(context: TrackingContext): UUID =
        context.visit.workerId ?: throw WorkerTrackingUnavailable("Booking visit has no assigned worker")

    private fun requireDisplayable(location: WorkerLocationResponse, expectedWorkerId: UUID) {
        if (location.workerId != expectedWorkerId) {
            throw WorkerLocationServiceUnavailable(IllegalStateException("Worker Service returned a mismatched worker"))
        }
        if (!isFresh(location)) throw WorkerTrackingUnavailable("Worker location is not fresh enough to display")
    }

    private fun isDisplayable(location: WorkerLocationResponse, expectedWorkerId: UUID): Boolean =
        location.workerId == expectedWorkerId && isFresh(location)

    private fun isFresh(location: WorkerLocationResponse): Boolean =
        !location.capturedAt.isBefore(clock.instant().minus(properties.maxLocationAge))

    private fun WorkerLocationResponse.toTracking(context: TrackingContext) = WorkerTrackingSnapshot(
        locationId,
        context.bookingId,
        context.visit.visitIndex,
        workerId,
        latitude,
        longitude,
        accuracyMeters,
        capturedAt,
        receivedAt,
    )
}
