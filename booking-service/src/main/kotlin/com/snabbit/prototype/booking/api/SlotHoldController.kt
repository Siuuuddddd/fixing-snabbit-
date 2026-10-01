package com.snabbit.prototype.booking.api

import com.snabbit.prototype.booking.application.HoldSlotCommand
import com.snabbit.prototype.booking.application.SlotHoldService
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.SlotHold
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class HoldSlotRequest(
    val bookingId: UUID,
    val customerId: UUID,
    val workerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val startsAt: Instant,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
)

data class SlotHoldResponse(
    val holdId: UUID,
    val bookingId: UUID,
    val workerId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val expiresAt: Instant,
) {
    companion object {
        fun from(hold: SlotHold) = SlotHoldResponse(
            holdId = hold.id,
            bookingId = hold.bookingId,
            workerId = hold.workerId,
            startsAt = hold.startsAt,
            endsAt = hold.endsAt,
            expiresAt = hold.expiresAt,
        )
    }
}

@RestController
@RequestMapping("/api/v1/bookings/slot-holds")
class SlotHoldController(private val slotHoldService: SlotHoldService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(@Valid @RequestBody request: HoldSlotRequest): SlotHoldResponse {
        val hold = slotHoldService.hold(
            HoldSlotCommand(
                bookingId = request.bookingId,
                customerId = request.customerId,
                workerId = request.workerId,
                serviceId = request.serviceId,
                bookingType = request.bookingType,
                startsAt = request.startsAt,
                duration = Duration.ofHours(request.durationHours),
            ),
        )
        return SlotHoldResponse.from(hold)
    }
}
