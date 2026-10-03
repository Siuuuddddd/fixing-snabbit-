package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.request.ActivatePlusCommand
import com.snabbit.prototype.user.dto.request.ActivatePlusRequest
import com.snabbit.prototype.user.dto.request.ConsumePassRequest
import com.snabbit.prototype.user.dto.request.ReservePassRequest
import com.snabbit.prototype.user.dto.response.PassReservationSnapshot
import com.snabbit.prototype.user.dto.response.PlusStatusSnapshot
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/users/{userId}/plus")
class InternalPlusController(private val service: PlusService) {
    @PostMapping("/subscriptions")
    fun activate(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ActivatePlusRequest,
    ): PlusStatusSnapshot = service.activate(
        userId,
        ActivatePlusCommand(
            request.sourceReference, request.startsAt, request.expiresAt,
            request.passCount, request.passDiscountMinor, request.serviceCode,
        ),
    )

    @PostMapping("/passes/reservations")
    fun reserve(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ReservePassRequest,
    ): PassReservationSnapshot = service.reservePass(
        userId, request.bookingId, request.serviceCode, request.grossTotalMinor, request.holdUntil,
    )

    @PostMapping("/passes/reservations/{bookingId}/consume")
    fun consume(@PathVariable bookingId: UUID, @RequestBody request: ConsumePassRequest) =
        service.consumePass(bookingId, request.confirmedAt)

    @PostMapping("/passes/reservations/{bookingId}/release")
    fun release(@PathVariable bookingId: UUID) = service.releasePass(bookingId)
}
