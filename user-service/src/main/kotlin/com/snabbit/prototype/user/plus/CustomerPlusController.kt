package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.user.dto.response.PlusStatusSnapshot

import com.snabbit.prototype.user.dto.request.PurchasePlusRequest

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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/plus")
class CustomerPlusController(
    private val service: PlusService,
    private val purchases: PlusPurchaseService,
) {
    @GetMapping
    fun status(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): PlusStatusSnapshot {
        if (authenticatedUserId != userId) throw CustomerAccessDenied()
        return service.status(userId)
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun purchase(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PurchasePlusRequest,
    ): PlusPurchaseSnapshot = purchases.purchase(
        userId, authenticatedUserId, request.walletId, request.planCode, idempotencyKey,
    )
}
