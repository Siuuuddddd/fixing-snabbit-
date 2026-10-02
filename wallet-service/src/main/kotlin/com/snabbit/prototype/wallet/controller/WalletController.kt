package com.snabbit.prototype.wallet.controller

import com.snabbit.prototype.wallet.service.BookingPaymentSnapshot
import com.snabbit.prototype.wallet.service.RefundSnapshot
import com.snabbit.prototype.wallet.service.RazorpayWebhookService
import com.snabbit.prototype.wallet.service.TopUpSnapshot
import com.snabbit.prototype.wallet.service.WalletBalance
import com.snabbit.prototype.wallet.service.WalletService
import com.snabbit.prototype.wallet.service.WalletOwnershipSnapshot
import com.snabbit.prototype.wallet.service.PlusPurchaseSnapshot
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

data class AddMoneyRequest(@field:Min(100) val amountMinor: Long)

data class CreateBookingPaymentRequest(
    val bookingId: UUID,
    val useWallet: Boolean = true,
)

data class CreatePlusPurchaseRequest(
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String? = null,
)

@RestController
@RequestMapping("/api/v1/wallets")
class WalletController(private val service: WalletService) {
    @GetMapping("/{walletId}")
    fun balance(
        @PathVariable walletId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): WalletBalance = service.balanceForCustomer(walletId, authenticatedCustomerId)

    @PostMapping("/{walletId}/top-ups")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMoney(
        @PathVariable walletId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: AddMoneyRequest,
    ): TopUpSnapshot = service.addMoneyForCustomer(
        walletId,
        authenticatedCustomerId,
        request.amountMinor,
        idempotencyKey,
    )
}

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

@RestController
class RazorpayWebhookController(private val webhookService: RazorpayWebhookService) {
    data class WebhookAcknowledgement(val result: String)

    @PostMapping("/webhooks/razorpay")
    fun webhook(
        @RequestHeader("X-Razorpay-Signature") signature: String,
        @RequestHeader("X-Razorpay-Event-Id") eventId: String,
        @RequestBody rawBody: ByteArray,
    ): WebhookAcknowledgement = WebhookAcknowledgement(webhookService.handle(rawBody, signature, eventId))
}
