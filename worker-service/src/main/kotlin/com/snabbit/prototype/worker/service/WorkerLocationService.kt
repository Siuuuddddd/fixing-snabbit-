package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerLocationPoint
import com.snabbit.prototype.worker.dto.request.WorkerLocationCommand
import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import com.snabbit.prototype.worker.exception.InvalidWorkerLocation
import com.snabbit.prototype.worker.repository.WorkerLocationRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerLocationService(
    private val workers: WorkerProfileRepository,
    private val locations: WorkerLocationRepository,
    private val clock: Clock,
    private val outbox: WorkerOutboxWriter,
) {
    @Transactional
    fun update(workerId: UUID, command: WorkerLocationCommand): WorkerLocationSnapshot {
        validate(command)
        val worker = workers.findForUpdate(workerId) ?: throw InvalidWorkerLocation("Worker does not exist")
        val now = clock.instant()
        worker.currentLatitude = command.latitude
        worker.currentLongitude = command.longitude
        worker.updatedAt = now
        val point = locations.save(
            WorkerLocationPoint(
                UUID.randomUUID(),
                workerId,
                command.latitude,
                command.longitude,
                command.accuracyMeters,
                command.capturedAt,
                now,
            ),
        )
        outbox.append(
            workerId,
            "WorkerLocationUpdated",
            mapOf(
                "workerId" to workerId, "locationId" to point.id, "latitude" to command.latitude,
                "longitude" to command.longitude, "accuracyMeters" to command.accuracyMeters,
                "capturedAt" to command.capturedAt,
            ),
            now,
        )
        return WorkerLocationSnapshot(
            point.id,
            workerId,
            command.latitude,
            command.longitude,
            command.accuracyMeters,
            command.capturedAt,
            point.receivedAt,
        )
    }

    @Transactional(readOnly = true)
    fun latest(workerId: UUID): WorkerLocationSnapshot? =
        locations.findFirstByWorkerIdOrderByReceivedAtDesc(workerId)?.toSnapshot()

    private fun validate(command: WorkerLocationCommand) {
        if (command.latitude.toDouble() !in -90.0..90.0 || command.longitude.toDouble() !in -180.0..180.0) {
            throw InvalidWorkerLocation("Valid coordinates are required")
        }
        if (command.accuracyMeters <= BigDecimal.ZERO || command.accuracyMeters > BigDecimal("100")) {
            throw InvalidWorkerLocation("Location accuracy must be within 100 metres")
        }
        val now = clock.instant()
        if (command.capturedAt.isBefore(now.minus(Duration.ofMinutes(5))) ||
            command.capturedAt.isAfter(now.plus(Duration.ofSeconds(30)))
        ) {
            throw InvalidWorkerLocation("Location timestamp is stale or in the future")
        }
    }

    private fun WorkerLocationPoint.toSnapshot() = WorkerLocationSnapshot(
        id,
        workerId,
        latitude,
        longitude,
        accuracyMeters,
        capturedAt,
        receivedAt,
    )
}
