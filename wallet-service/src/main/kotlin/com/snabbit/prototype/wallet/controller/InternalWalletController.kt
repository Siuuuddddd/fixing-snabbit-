package com.snabbit.prototype.wallet.controller

import com.snabbit.prototype.wallet.dto.response.WalletOwnershipSnapshot

import com.snabbit.prototype.wallet.dto.response.WalletBalance

import com.snabbit.prototype.wallet.dto.response.TopUpSnapshot

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import com.snabbit.prototype.wallet.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.wallet.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.wallet.dto.request.CreatePlusPurchaseRequest

import com.snabbit.prototype.wallet.dto.request.CreateBookingPaymentRequest

import com.snabbit.prototype.wallet.service.RazorpayWebhookService
import com.snabbit.prototype.wallet.service.WalletService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/wallets")
class InternalWalletController(private val service: WalletService) {
    @GetMapping("/{walletId}/ownership")
    fun ownership(@PathVariable walletId: UUID): WalletOwnershipSnapshot = service.ownership(walletId)

    @PostMapping("/{walletId}/plus-purchases")
    fun createPlusPurchase(
        @PathVariable walletId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreatePlusPurchaseRequest,
    ): PlusPurchaseSnapshot = service.createPlusPurchase(
        walletId, request.customerId, request.planCode, request.amountMinor,
        request.durationDays, request.passCount, request.passDiscountMinor,
        request.serviceCode, idempotencyKey,
    )

    @PostMapping("/{walletId}/booking-payments")
    fun createBookingPayment(
        @PathVariable walletId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateBookingPaymentRequest,
    ): BookingPaymentSnapshot = service.createBookingPayment(
        walletId = walletId,
        bookingId = request.bookingId,
        useWallet = request.useWallet,
        idempotencyKey = idempotencyKey,
    )

    @PostMapping("/booking-payments/{paymentId}/refunds")
    fun refund(
        @PathVariable paymentId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): RefundSnapshot = service.refund(paymentId, idempotencyKey)

    @PostMapping("/booking-payments/{paymentId}/cancel")
    fun cancelPayment(@PathVariable paymentId: UUID): BookingPaymentSnapshot =
        service.cancelBookingPayment(paymentId)
}
