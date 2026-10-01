package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.SlotHoldStatus
import com.snabbit.prototype.booking.persistence.SlotHoldRepository
import com.snabbit.prototype.booking.persistence.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.support.SlotUnavailable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class SlotHoldService(
    private val bookingWindowPolicy: BookingWindowPolicy,
    private val capacityRepository: WorkerDayCapacityRepository,
    private val slotHoldRepository: SlotHoldRepository,
    private val properties: BookingProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun hold(command: HoldSlotCommand): SlotHold {
        bookingWindowPolicy.validate(command.bookingType, command.startsAt, command.duration)

        val now = clock.instant()
        val endsAt = command.startsAt.plus(command.duration)
        val serviceDate = command.startsAt.atZone(properties.zoneId).toLocalDate()
        val localStart = command.startsAt.atZone(properties.zoneId).toLocalTime()
        val localEnd = endsAt.atZone(properties.zoneId).toLocalTime()

        val capacity = capacityRepository.findForUpdate(command.workerId, serviceDate)
            ?: throw SlotUnavailable("Worker has no availability on the requested day")

        if (localStart < capacity.availableFrom || localEnd > capacity.availableUntil) {
            throw SlotUnavailable("Requested visit is outside the worker's availability")
        }
        if (slotHoldRepository.hasActiveOverlap(command.workerId, command.startsAt, endsAt, now)) {
            throw SlotUnavailable("Requested worker slot is no longer available")
        }

        return slotHoldRepository.save(
            SlotHold(
                id = UUID.randomUUID(),
                bookingId = command.bookingId,
                customerId = command.customerId,
                workerId = command.workerId,
                serviceId = command.serviceId,
                startsAt = command.startsAt,
                endsAt = endsAt,
                status = SlotHoldStatus.HELD,
                expiresAt = now.plus(properties.slotHoldDuration),
                createdAt = now,
            ),
        )
    }
}
