package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.dto.request.WorkerAvailabilityCommand
import com.snabbit.prototype.worker.dto.response.WorkerAvailabilitySnapshot
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import com.snabbit.prototype.worker.exception.InvalidWorkerAvailability
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerAvailabilityService(
    private val workers: WorkerProfileRepository,
    private val availability: WorkerAvailabilityRepository,
    private val clock: Clock,
    private val outbox: WorkerOutboxWriter,
) {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val openingTime = LocalTime.of(8, 0)
    private val closingTime = LocalTime.of(19, 0)

    @Transactional(readOnly = true)
    fun list(workerId: UUID, from: LocalDate?, to: LocalDate?): List<WorkerAvailabilitySnapshot> {
        val today = LocalDate.now(clock.withZone(zone))
        val rangeStart = from ?: today
        val rangeEnd = to ?: today.plusDays(7)
        validateRange(rangeStart, rangeEnd, today)
        if (!workers.existsById(workerId)) throw InvalidWorkerAvailability("Worker was not found")
        return availability.findAllByWorkerIdAndServiceDateBetweenOrderByServiceDate(
            workerId,
            rangeStart,
            rangeEnd,
        ).map(::snapshot)
    }

    @Transactional
    fun update(
        workerId: UUID,
        serviceDate: LocalDate,
        command: WorkerAvailabilityCommand,
    ): WorkerAvailabilitySnapshot {
        val today = LocalDate.now(clock.withZone(zone))
        if (serviceDate.isBefore(today) || serviceDate.isAfter(today.plusDays(30))) {
            throw InvalidWorkerAvailability("Availability can be changed only from today through the next 30 days")
        }
        workers.findForUpdate(workerId) ?: throw InvalidWorkerAvailability("Worker was not found")
        val (availableFrom, availableUntil) = window(command)
        val row = availability.findByWorkerIdAndServiceDate(workerId, serviceDate)
            ?.apply {
                this.availableFrom = availableFrom
                this.availableUntil = availableUntil
                this.status = command.status
            }
            ?: WorkerAvailability(
                id = UUID.randomUUID(),
                workerId = workerId,
                serviceDate = serviceDate,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                status = command.status,
            )
        val saved = availability.save(row)
        val occurredAt = clock.instant()
        outbox.append(
            workerId,
            "WorkerAvailabilityUpdated",
            mapOf(
                "workerId" to workerId, "serviceDate" to serviceDate, "status" to saved.status.name,
                "availableFrom" to saved.availableFrom, "availableUntil" to saved.availableUntil,
            ),
            occurredAt,
        )
        return snapshot(saved)
    }

    private fun validateRange(from: LocalDate, to: LocalDate, today: LocalDate) {
        if (from.isBefore(today) || to.isBefore(from) || to.isAfter(today.plusDays(30))) {
            throw InvalidWorkerAvailability("Availability range must be within today and the next 30 days")
        }
    }

    private fun window(command: WorkerAvailabilityCommand): Pair<LocalTime, LocalTime> {
        if (command.status == AvailabilityStatus.UNAVAILABLE) return openingTime to closingTime
        val from = command.availableFrom
            ?: throw InvalidWorkerAvailability("availableFrom is required when status is AVAILABLE")
        val until = command.availableUntil
            ?: throw InvalidWorkerAvailability("availableUntil is required when status is AVAILABLE")
        if (from < openingTime || until > closingTime || from >= until) {
            throw InvalidWorkerAvailability("Availability must fit within operating hours 08:00-19:00")
        }
        return from to until
    }

    private fun snapshot(row: WorkerAvailability) = WorkerAvailabilitySnapshot(
        serviceDate = row.serviceDate,
        availableFrom = row.availableFrom,
        availableUntil = row.availableUntil,
        status = row.status,
    )
}
