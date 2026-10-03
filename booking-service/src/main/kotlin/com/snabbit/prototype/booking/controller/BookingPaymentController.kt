package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand
import com.snabbit.prototype.booking.dto.request.StartPaymentRequest
import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.payment.BookingPaymentService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class BookingPaymentController(private val paymentService: BookingPaymentService) {
    @PostMapping("/{bookingId}/payments")
    fun startPayment(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: StartPaymentRequest,
    ): BookingPaymentSnapshot = paymentService.start(
        StartBookingPaymentCommand(
            walletId = request.walletId,
            bookingId = bookingId,
            authenticatedCustomerId = authenticatedCustomerId,
            useWallet = request.useWallet,
            idempotencyKey = idempotencyKey,
        ),
    )
}

internal fun requireOwner(authenticatedCustomerId: UUID, requestedCustomerId: UUID) {
    if (authenticatedCustomerId != requestedCustomerId) throw CustomerAccessDenied()
}
