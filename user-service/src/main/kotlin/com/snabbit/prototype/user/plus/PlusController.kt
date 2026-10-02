package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.support.CustomerAccessDenied
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class ActivatePlusRequest(
    @field:NotBlank @field:Size(max = 120) val sourceReference: String,
    val startsAt: Instant,
    val expiresAt: Instant,
    @field:Min(0) @field:Max(20) val passCount: Int,
    @field:Min(0) val passDiscountMinor: Long,
    @field:Size(max = 60) val serviceCode: String? = null,
)

data class ReservePassRequest(
    val bookingId: UUID,
    @field:NotBlank @field:Size(max = 60) val serviceCode: String,
    @field:Min(1) val grossTotalMinor: Long,
    val holdUntil: Instant,
)

data class ConsumePassRequest(val confirmedAt: Instant)

@RestController
@RequestMapping("/api/v1/users/{userId}/plus")
class CustomerPlusController(private val service: PlusService) {
    @GetMapping
    fun status(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): PlusStatusSnapshot {
        if (authenticatedUserId != userId) throw CustomerAccessDenied()
        return service.status(userId)
    }
}

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
