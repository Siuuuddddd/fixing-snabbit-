package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.dto.response.WebhookOutcome

import com.snabbit.prototype.wallet.dto.response.WalletOwnershipSnapshot

import com.snabbit.prototype.wallet.dto.response.WalletBalance

import com.snabbit.prototype.wallet.dto.response.TopUpSnapshot

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import com.snabbit.prototype.wallet.dto.response.RefundExecution

import com.snabbit.prototype.wallet.dto.response.ProviderCapture

import com.snabbit.prototype.wallet.dto.response.PreparedRefund

import com.snabbit.prototype.wallet.dto.response.ProviderRefund

import com.snabbit.prototype.wallet.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.wallet.dto.response.ProviderOrder

import com.snabbit.prototype.wallet.dto.response.FinanceRefundSnapshot

import com.snabbit.prototype.wallet.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.wallet.domain.enums.TopUpStatus

import com.snabbit.prototype.wallet.domain.enums.ReservationStatus

import com.snabbit.prototype.wallet.domain.enums.RefundStatus

import com.snabbit.prototype.wallet.domain.enums.PlusPurchaseStatus

import com.snabbit.prototype.wallet.domain.enums.LedgerEntryType

import com.snabbit.prototype.wallet.domain.enums.BookingPaymentStatus

import com.snabbit.prototype.wallet.config.WalletProperties
import com.snabbit.prototype.wallet.domain.BookingPayment
import com.snabbit.prototype.wallet.domain.PaymentRefund
import com.snabbit.prototype.wallet.domain.TopUpOrder
import com.snabbit.prototype.wallet.domain.PlusPurchase
import com.snabbit.prototype.wallet.domain.WalletLedgerEntry
import com.snabbit.prototype.wallet.domain.WalletReservation
import com.snabbit.prototype.wallet.repository.BookingPaymentRepository
import com.snabbit.prototype.wallet.repository.PaymentRefundRepository
import com.snabbit.prototype.wallet.repository.ProcessedWebhookStore
import com.snabbit.prototype.wallet.repository.TopUpOrderRepository
import com.snabbit.prototype.wallet.repository.PlusPurchaseRepository
import com.snabbit.prototype.wallet.repository.WalletAccountRepository
import com.snabbit.prototype.wallet.repository.WalletLedgerRepository
import com.snabbit.prototype.wallet.repository.WalletReservationRepository
import com.snabbit.prototype.wallet.support.PaymentConflict
import com.snabbit.prototype.wallet.support.PaymentNotFound
import com.snabbit.prototype.wallet.support.WalletNotFound
import com.snabbit.prototype.wallet.support.WalletRuleViolation
import com.snabbit.prototype.wallet.support.WalletAccessDenied
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.min

@Service
class WalletTransactionService(
    private val walletRepository: WalletAccountRepository,
    private val ledgerRepository: WalletLedgerRepository,
    private val reservationRepository: WalletReservationRepository,
    private val topUpRepository: TopUpOrderRepository,
    private val plusPurchaseRepository: PlusPurchaseRepository,
    private val paymentRepository: BookingPaymentRepository,
    private val refundRepository: PaymentRefundRepository,
    private val webhookStore: ProcessedWebhookStore,
    private val outboxWriter: WalletOutboxWriter,
    private val properties: WalletProperties,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun ownership(walletId: UUID): WalletOwnershipSnapshot {
        val wallet = walletRepository.findById(walletId).orElseThrow { WalletNotFound(walletId) }
        return WalletOwnershipSnapshot(wallet.id, wallet.customerId)
    }
    @Transactional(readOnly = true)
    fun balance(walletId: UUID): WalletBalance {
        val wallet = walletRepository.findById(walletId).orElseThrow { WalletNotFound(walletId) }
        val now = clock.instant()
        val ledgerBalance = ledgerRepository.netBalance(walletId)
        val reserved = reservationRepository.activeTotal(walletId, now)
        return WalletBalance(
            walletId = wallet.id,
            currency = wallet.currency,
            ledgerBalanceMinor = ledgerBalance,
            reservedMinor = reserved,
            availableMinor = ledgerBalance - reserved,
            totalSpentMinor = ledgerRepository.totalSpent(walletId),
        )
    }

    @Transactional(readOnly = true)
    fun requireOwnership(walletId: UUID, customerId: UUID) {
        val wallet = walletRepository.findById(walletId).orElseThrow { WalletNotFound(walletId) }
        if (wallet.customerId != customerId) throw WalletAccessDenied()
    }

    @Transactional
    fun prepareTopUp(walletId: UUID, amountMinor: Long, idempotencyKey: String): TopUpSnapshot {
        validateAmount(amountMinor)
        validateIdempotencyKey(idempotencyKey)
        walletRepository.findForUpdate(walletId) ?: throw WalletNotFound(walletId)
        topUpRepository.findByIdempotencyKey(idempotencyKey)?.let { existing ->
            if (existing.walletId != walletId || existing.amountMinor != amountMinor) {
                throw PaymentConflict("Idempotency key was used with different top-up details")
            }
            return topUpSnapshot(existing)
        }
        val now = clock.instant()
        val order = topUpRepository.save(
            TopUpOrder(
                id = UUID.randomUUID(),
                walletId = walletId,
                amountMinor = amountMinor,
                status = TopUpStatus.CREATING_ORDER,
                provider = "RAZORPAY",
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return topUpSnapshot(order)
    }

    @Transactional
    fun attachTopUpOrder(topUpId: UUID, providerOrder: ProviderOrder): TopUpSnapshot {
        val topUp = topUpRepository.findForUpdate(topUpId) ?: throw PaymentNotFound("Top-up $topUpId was not found")
        if (topUp.status == TopUpStatus.PENDING) return topUpSnapshot(topUp)
        if (providerOrder.amountMinor != topUp.amountMinor || providerOrder.currency != properties.currency) {
            topUp.fail(clock.instant())
            throw PaymentConflict("Provider order amount or currency does not match the top-up")
        }
        topUp.orderCreated(providerOrder.id, clock.instant())
        return topUpSnapshot(topUp)
    }

    @Transactional
    fun preparePlusPurchase(
        walletId: UUID,
        customerId: UUID,
        planCode: String,
        amountMinor: Long,
        durationDays: Int,
        passCount: Int,
        passDiscountMinor: Long,
        serviceCode: String?,
        idempotencyKey: String,
    ): PlusPurchaseSnapshot {
        validateAmount(amountMinor)
        validateIdempotencyKey(idempotencyKey)
        if (planCode.isBlank() || planCode.length > 60) throw WalletRuleViolation("A valid Plus plan code is required")
        if (durationDays !in 1..365) throw WalletRuleViolation("Plus duration must be between 1 and 365 days")
        if (passCount !in 0..20) throw WalletRuleViolation("Plus pass count must be between 0 and 20")
        if (passDiscountMinor < 0 || passDiscountMinor % 100 != 0L ||
            (passCount > 0 && passDiscountMinor < 100)
        ) {
            throw WalletRuleViolation("Plus pass discount is invalid")
        }
        val wallet = walletRepository.findForUpdate(walletId) ?: throw WalletNotFound(walletId)
        if (wallet.customerId != customerId) throw WalletAccessDenied()
        val normalizedPlan = planCode.trim().uppercase()
        val normalizedService = serviceCode?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        plusPurchaseRepository.findByIdempotencyKey(idempotencyKey)?.let { existing ->
            if (existing.walletId != walletId || existing.customerId != customerId ||
                existing.planCode != normalizedPlan || existing.amountMinor != amountMinor
            ) {
                throw PaymentConflict("Idempotency key was used with different Plus purchase details")
            }
            return plusPurchaseSnapshot(existing)
        }
        val now = clock.instant()
        val purchase = plusPurchaseRepository.save(
            PlusPurchase(
                UUID.randomUUID(), walletId, customerId, normalizedPlan, amountMinor,
                durationDays, passCount, passDiscountMinor, normalizedService,
                PlusPurchaseStatus.CREATING_ORDER, "RAZORPAY", idempotencyKey = idempotencyKey,
                createdAt = now, updatedAt = now,
            ),
        )
        return plusPurchaseSnapshot(purchase)
    }

    @Transactional
    fun attachPlusPurchaseOrder(purchaseId: UUID, providerOrder: ProviderOrder): PlusPurchaseSnapshot {
        val purchase = plusPurchaseRepository.findForUpdate(purchaseId)
            ?: throw PaymentNotFound("Plus purchase $purchaseId was not found")
        if (purchase.status == PlusPurchaseStatus.PENDING) return plusPurchaseSnapshot(purchase)
        if (providerOrder.amountMinor != purchase.amountMinor || providerOrder.currency != properties.currency) {
            purchase.fail(clock.instant())
            throw PaymentConflict("Provider order amount or currency does not match the Plus purchase")
        }
        purchase.orderCreated(providerOrder.id, clock.instant())
        return plusPurchaseSnapshot(purchase)
    }

    @Transactional
    fun prepareBookingPayment(
        walletId: UUID,
        bookingId: UUID,
        customerId: UUID,
        totalAmountMinor: Long,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPaymentSnapshot {
        validateAmount(totalAmountMinor)
        validateIdempotencyKey(idempotencyKey)
        val wallet = walletRepository.findForUpdate(walletId) ?: throw WalletNotFound(walletId)
        if (wallet.customerId != customerId) {
            throw PaymentConflict("Wallet does not belong to the booking customer")
        }
        paymentRepository.findByIdempotencyKey(idempotencyKey)?.let { existing ->
            if (existing.walletId != walletId || existing.bookingId != bookingId ||
                existing.totalAmountMinor != totalAmountMinor
            ) {
                throw PaymentConflict("Idempotency key was used with different booking payment details")
            }
            return paymentSnapshot(existing)
        }

        val now = clock.instant()
        val available = (ledgerRepository.netBalance(wallet.id) - reservationRepository.activeTotal(wallet.id, now))
            .coerceAtLeast(0)
        val walletAmount = if (useWallet) min(available, totalAmountMinor) else 0
        val onlineAmount = totalAmountMinor - walletAmount
        val reservation = if (walletAmount > 0) {
            reservationRepository.save(
                WalletReservation(
                    id = UUID.randomUUID(),
                    walletId = wallet.id,
                    bookingId = bookingId,
                    amountMinor = walletAmount,
                    status = ReservationStatus.ACTIVE,
                    expiresAt = now.plus(properties.reservationDuration),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            null
        }
        val payment = paymentRepository.save(
            BookingPayment(
                id = UUID.randomUUID(),
                bookingId = bookingId,
                walletId = wallet.id,
                reservationId = reservation?.id,
                totalAmountMinor = totalAmountMinor,
                walletAmountMinor = walletAmount,
                onlineAmountMinor = onlineAmount,
                status = BookingPaymentStatus.CREATING_ORDER,
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (onlineAmount == 0L) {
            captureReservation(payment, now)
            payment.complete(null, now)
            paymentSucceeded(payment, now)
        }
        return paymentSnapshot(payment)
    }

    @Transactional(readOnly = true)
    fun existingBookingPayment(
        walletId: UUID,
        bookingId: UUID,
        idempotencyKey: String,
    ): BookingPaymentSnapshot? {
        validateIdempotencyKey(idempotencyKey)
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey) ?: return null
        if (existing.walletId != walletId || existing.bookingId != bookingId) {
            throw PaymentConflict("Idempotency key was used with different booking payment details")
        }
        return paymentSnapshot(existing)
    }

    @Transactional
    fun attachBookingProviderOrder(paymentId: UUID, providerOrder: ProviderOrder): BookingPaymentSnapshot {
        val payment = paymentRepository.findForUpdate(paymentId)
            ?: throw PaymentNotFound("Booking payment $paymentId was not found")
        if (payment.status == BookingPaymentStatus.PENDING) return paymentSnapshot(payment)
        if (providerOrder.amountMinor != payment.onlineAmountMinor || providerOrder.currency != properties.currency) {
            failPaymentAndRelease(payment, clock.instant())
            throw PaymentConflict("Provider order amount or currency does not match the booking payment")
        }
        payment.providerOrderCreated(providerOrder.id, clock.instant())
        return paymentSnapshot(payment)
    }

    @Transactional
    fun cancelBookingPayment(paymentId: UUID): BookingPaymentSnapshot {
        val payment = paymentRepository.findForUpdate(paymentId)
            ?: throw PaymentNotFound("Booking payment $paymentId was not found")
        if (payment.status == BookingPaymentStatus.FAILED) return paymentSnapshot(payment)
        if (payment.status != BookingPaymentStatus.CREATING_ORDER && payment.status != BookingPaymentStatus.PENDING) {
            throw PaymentConflict("Only a pending booking payment can be cancelled")
        }
        failPaymentAndRelease(payment, clock.instant())
        return paymentSnapshot(payment)
    }

    @Transactional
    fun terminateBookingPayment(
        bookingId: UUID,
        sourceEventId: UUID,
        requestedRefundMinor: Long? = null,
    ): UUID? {
        val payment = paymentRepository.findByBookingIdForUpdate(bookingId) ?: return null
        val now = clock.instant()
        return when (payment.status) {
            BookingPaymentStatus.CREATING_ORDER,
            BookingPaymentStatus.PENDING,
            -> {
                failPaymentAndRelease(payment, now)
                null
            }
            BookingPaymentStatus.COMPLETED -> {
                refundRepository.findByBookingPaymentId(payment.id)?.id ?: run {
                    val refundTotal = requestedRefundMinor ?: payment.totalAmountMinor
                    if (refundTotal !in 0..payment.totalAmountMinor) {
                        throw PaymentConflict("Requested refund must be between zero and the captured payment")
                    }
                    val retainedFee = payment.totalAmountMinor - refundTotal
                    val retainedOnline = minOf(payment.onlineAmountMinor, retainedFee)
                    val refundOnline = payment.onlineAmountMinor - retainedOnline
                    val refundWallet = payment.walletAmountMinor - (retainedFee - retainedOnline)
                    payment.beginRefund(now)
                    val refund = refundRepository.save(
                        PaymentRefund(
                            id = UUID.randomUUID(),
                            bookingPaymentId = payment.id,
                            walletId = payment.walletId,
                            walletAmountMinor = refundWallet,
                            onlineAmountMinor = refundOnline,
                            status = RefundStatus.CREATING,
                            idempotencyKey = "booking-termination:$sourceEventId",
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    outboxWriter.append(
                        bookingId,
                        "WalletRefundQueued",
                        mapOf(
                            "bookingId" to bookingId,
                            "paymentId" to payment.id,
                            "refundId" to refund.id,
                            "amountMinor" to refundTotal,
                            "retainedFeeMinor" to retainedFee,
                        ),
                        now,
                    )
                    refund.id
                }
            }
            BookingPaymentStatus.REFUND_PENDING -> refundRepository.findByBookingPaymentId(payment.id)?.id
            BookingPaymentStatus.FAILED,
            BookingPaymentStatus.REFUNDED,
            -> null
        }
    }

    @Transactional
    fun processCapture(capture: ProviderCapture): WebhookOutcome {
        val now = clock.instant()
        if (!webhookStore.claim(
                "RAZORPAY",
                capture.eventId,
                capture.eventType,
                capture.providerOrderId,
                capture.providerPaymentId,
                capture.amountMinor,
                now,
            )
        ) {
            return WebhookOutcome("DUPLICATE")
        }

        topUpRepository.findByProviderOrderIdForUpdate(capture.providerOrderId)?.let { topUp ->
            if (topUp.status == TopUpStatus.PAID) {
                return completeCapture(capture, WebhookOutcome("ALREADY_PROCESSED"), now)
            }
            if (topUp.status != TopUpStatus.PENDING) {
                return completeCapture(capture, WebhookOutcome("IGNORED"), now)
            }
            if (capture.amountMinor != topUp.amountMinor) {
                throw PaymentConflict("Captured top-up amount does not match the provider order")
            }
            topUp.paid(capture.providerPaymentId, now)
            appendLedger(
                walletId = topUp.walletId,
                type = LedgerEntryType.CREDIT,
                amountMinor = topUp.amountMinor,
                referenceType = "TOP_UP",
                referenceId = topUp.id.toString(),
                idempotencyKey = "top-up:${topUp.id}",
                description = "Wallet top-up",
                now = now,
            )
            outboxWriter.append(
                topUp.walletId,
                "WalletTopUpSucceeded",
                mapOf("walletId" to topUp.walletId, "topUpId" to topUp.id, "amountMinor" to topUp.amountMinor),
                now,
            )
            return completeCapture(capture, WebhookOutcome("TOP_UP_CREDITED"), now)
        }

        plusPurchaseRepository.findByProviderOrderIdForUpdate(capture.providerOrderId)?.let { purchase ->
            if (purchase.status == PlusPurchaseStatus.PAID) {
                return completeCapture(capture, WebhookOutcome("ALREADY_PROCESSED"), now)
            }
            if (purchase.status != PlusPurchaseStatus.PENDING) {
                return completeCapture(capture, WebhookOutcome("IGNORED"), now)
            }
            if (capture.amountMinor != purchase.amountMinor) {
                throw PaymentConflict("Captured Plus purchase amount does not match the provider order")
            }
            purchase.paid(capture.providerPaymentId, now)
            outboxWriter.append(
                purchase.walletId,
                "PlusSubscriptionPurchased",
                mapOf(
                    "purchaseId" to purchase.id,
                    "walletId" to purchase.walletId,
                    "customerId" to purchase.customerId,
                    "planCode" to purchase.planCode,
                    "amountMinor" to purchase.amountMinor,
                    "durationDays" to purchase.durationDays,
                    "passCount" to purchase.passCount,
                    "passDiscountMinor" to purchase.passDiscountMinor,
                    "serviceCode" to (purchase.serviceCode ?: ""),
                    "providerPaymentId" to capture.providerPaymentId,
                ),
                now,
            )
            return completeCapture(capture, WebhookOutcome("PLUS_SUBSCRIPTION_PURCHASED"), now)
        }

        paymentRepository.findByProviderOrderIdForUpdate(capture.providerOrderId)?.let { payment ->
            if (payment.status == BookingPaymentStatus.COMPLETED) {
                return completeCapture(capture, WebhookOutcome("ALREADY_PROCESSED"), now)
            }
            if (capture.amountMinor != payment.onlineAmountMinor) {
                throw PaymentConflict("Captured booking amount does not match the provider order")
            }
            if (payment.status == BookingPaymentStatus.FAILED) {
                return completeCapture(capture, queueCompensationRefund(payment, capture, now), now)
            }
            if (payment.status != BookingPaymentStatus.PENDING) {
                return completeCapture(capture, WebhookOutcome("IGNORED"), now)
            }
            val reservation = payment.reservationId?.let(reservationRepository::findForUpdate)
            if (reservation != null &&
                (reservation.status != ReservationStatus.ACTIVE || !reservation.expiresAt.isAfter(now))
            ) {
                reservation.expire(now)
                return completeCapture(capture, queueCompensationRefund(payment, capture, now), now)
            }
            captureReservation(payment, now)
            payment.complete(capture.providerPaymentId, now)
            paymentSucceeded(payment, now)
            return completeCapture(capture, WebhookOutcome("BOOKING_PAYMENT_COMPLETED"), now)
        }

        return WebhookOutcome("PENDING_UNKNOWN_ORDER")
    }

    @Transactional(readOnly = true)
    fun pendingCaptures(): List<ProviderCapture> = webhookStore.pendingCaptures("RAZORPAY", 100).map { stored ->
        ProviderCapture(
            eventId = stored.eventId,
            eventType = stored.eventType,
            providerOrderId = stored.providerOrderId,
            providerPaymentId = stored.providerPaymentId,
            amountMinor = stored.amountMinor,
        )
    }

    @Transactional
    fun prepareFullRefund(paymentId: UUID, idempotencyKey: String): PreparedRefund {
        validateIdempotencyKey(idempotencyKey)
        refundRepository.findByIdempotencyKey(idempotencyKey)?.let { existing ->
            val payment = paymentRepository.findById(existing.bookingPaymentId)
                .orElseThrow { PaymentNotFound("Booking payment was not found") }
            return PreparedRefund(refundSnapshot(existing), payment.providerPaymentId)
        }
        val payment = paymentRepository.findForUpdate(paymentId)
            ?: throw PaymentNotFound("Booking payment $paymentId was not found")
        if (payment.status != BookingPaymentStatus.COMPLETED) {
            throw PaymentConflict("Only completed booking payments can be refunded")
        }
        val now = clock.instant()
        payment.beginRefund(now)
        val refund = refundRepository.save(
            PaymentRefund(
                id = UUID.randomUUID(),
                bookingPaymentId = payment.id,
                walletId = payment.walletId,
                walletAmountMinor = payment.walletAmountMinor,
                onlineAmountMinor = payment.onlineAmountMinor,
                status = RefundStatus.CREATING,
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return PreparedRefund(refundSnapshot(refund), payment.providerPaymentId)
    }

    @Transactional
    fun completeRefund(refundId: UUID, providerRefund: ProviderRefund?): RefundSnapshot {
        val refund = refundRepository.findForUpdate(refundId)
            ?: throw PaymentNotFound("Refund $refundId was not found")
        if (refund.status == RefundStatus.COMPLETED) return refundSnapshot(refund)
        if (refund.status != RefundStatus.PROCESSING) {
            throw PaymentConflict("Refund is not being processed")
        }
        if (refund.onlineAmountMinor > 0 && providerRefund == null) {
            throw PaymentConflict("Provider refund is required")
        }
        if (providerRefund != null && providerRefund.amountMinor != refund.onlineAmountMinor) {
            throw PaymentConflict("Provider refund amount does not match")
        }
        val payment = paymentRepository.findForUpdate(refund.bookingPaymentId)
            ?: throw PaymentNotFound("Booking payment was not found")
        val now = clock.instant()
        if (refund.walletAmountMinor > 0) {
            appendLedger(
                walletId = refund.walletId,
                type = LedgerEntryType.CREDIT,
                amountMinor = refund.walletAmountMinor,
                referenceType = "BOOKING_REFUND",
                referenceId = refund.id.toString(),
                idempotencyKey = "booking-refund:${refund.id}",
                description = "Booking refund",
                now = now,
            )
        }
        refund.complete(providerRefund?.id, now)
        payment.refunded(now)
        outboxWriter.append(
            payment.bookingId,
            "WalletRefundCompleted",
            mapOf(
                "bookingId" to payment.bookingId,
                "paymentId" to payment.id,
                "refundId" to refund.id,
                "amountMinor" to (refund.walletAmountMinor + refund.onlineAmountMinor),
            ),
            now,
        )
        return refundSnapshot(refund)
    }

    @Transactional
    fun claimRefund(refundId: UUID): RefundExecution? {
        val refund = refundRepository.findForUpdate(refundId)
            ?: throw PaymentNotFound("Refund $refundId was not found")
        if (refund.status == RefundStatus.COMPLETED) return null
        val now = clock.instant()
        if (refund.status == RefundStatus.PROCESSING &&
            refund.updatedAt.isAfter(now.minus(properties.refundProcessingLease))
        ) {
            return null
        }
        if (refund.status != RefundStatus.CREATING && refund.status != RefundStatus.PROCESSING) {
            throw PaymentConflict("Refund cannot be processed")
        }
        val payment = paymentRepository.findById(refund.bookingPaymentId)
            .orElseThrow { PaymentNotFound("Booking payment was not found") }
        refund.beginProcessing(now)
        return RefundExecution(refund.id, payment.providerPaymentId, refund.onlineAmountMinor)
    }

    @Transactional
    fun retryRefund(refundId: UUID) {
        refundRepository.findForUpdate(refundId)?.retryLater(clock.instant())
    }

    @Transactional(readOnly = true)
    fun pendingRefundIds(): List<UUID> =
        refundRepository.findRetryable(
            RefundStatus.CREATING,
            RefundStatus.PROCESSING,
            clock.instant().minus(properties.refundProcessingLease),
            PageRequest.of(0, 100),
        ).map(PaymentRefund::id)

    @Transactional(readOnly = true)
    fun financeRefunds(status: RefundStatus?): List<FinanceRefundSnapshot> {
        val refunds = if (status == null) {
            refundRepository.findTop100ByOrderByUpdatedAtDesc()
        } else {
            refundRepository.findTop100ByStatusOrderByUpdatedAtDesc(status)
        }
        return refunds.map { refund ->
            FinanceRefundSnapshot(
                refundId = refund.id,
                bookingPaymentId = refund.bookingPaymentId,
                walletId = refund.walletId,
                status = refund.status.name,
                walletAmountMinor = refund.walletAmountMinor,
                onlineAmountMinor = refund.onlineAmountMinor,
                providerRefundId = refund.providerRefundId,
                createdAt = refund.createdAt,
                updatedAt = refund.updatedAt,
            )
        }
    }

    @Transactional(readOnly = true)
    fun refund(refundId: UUID): RefundSnapshot = refundRepository.findById(refundId)
        .map(::refundSnapshot)
        .orElseThrow { PaymentNotFound("Refund $refundId was not found") }

    private fun createCompensationRefund(payment: BookingPayment, now: Instant): PaymentRefund =
        refundRepository.save(
            PaymentRefund(
                id = UUID.randomUUID(),
                bookingPaymentId = payment.id,
                walletId = payment.walletId,
                walletAmountMinor = 0,
                onlineAmountMinor = payment.onlineAmountMinor,
                status = RefundStatus.CREATING,
                idempotencyKey = "late-capture:${payment.id}",
                createdAt = now,
                updatedAt = now,
            ),
        )

    private fun queueCompensationRefund(
        payment: BookingPayment,
        capture: ProviderCapture,
        now: Instant,
    ): WebhookOutcome {
        refundRepository.findByBookingPaymentId(payment.id)?.let { existing ->
            return WebhookOutcome(
                result = "REFUND_REQUIRED",
                compensationRefundId = existing.id,
                providerPaymentId = capture.providerPaymentId,
                refundAmountMinor = capture.amountMinor,
            )
        }
        payment.beginCompensationRefund(capture.providerPaymentId, now)
        val refund = createCompensationRefund(payment, now)
        outboxWriter.append(
            payment.bookingId,
            "WalletPaymentRefundRequired",
            mapOf("bookingId" to payment.bookingId, "paymentId" to payment.id, "refundId" to refund.id),
            now,
        )
        return WebhookOutcome(
            result = "REFUND_REQUIRED",
            compensationRefundId = refund.id,
            providerPaymentId = capture.providerPaymentId,
            refundAmountMinor = capture.amountMinor,
        )
    }

    private fun captureReservation(payment: BookingPayment, now: Instant) {
        if (payment.walletAmountMinor == 0L) return
        val reservation = payment.reservationId?.let(reservationRepository::findForUpdate)
            ?: throw PaymentConflict("Wallet reservation is missing")
        reservation.capture(now)
        appendLedger(
            walletId = payment.walletId,
            type = LedgerEntryType.DEBIT,
            amountMinor = payment.walletAmountMinor,
            referenceType = "BOOKING_PAYMENT",
            referenceId = payment.bookingId.toString(),
            idempotencyKey = "booking-debit:${payment.id}",
            description = "Booking payment",
            now = now,
        )
    }

    private fun paymentSucceeded(payment: BookingPayment, now: Instant) {
        outboxWriter.append(
            payment.bookingId,
            "WalletPaymentSucceeded",
            mapOf(
                "bookingId" to payment.bookingId,
                "paymentId" to payment.id,
                "walletAmountMinor" to payment.walletAmountMinor,
                "onlineAmountMinor" to payment.onlineAmountMinor,
            ),
            now,
        )
    }

    private fun completeCapture(
        capture: ProviderCapture,
        outcome: WebhookOutcome,
        now: Instant,
    ): WebhookOutcome {
        webhookStore.complete("RAZORPAY", capture.eventId, now)
        return outcome
    }

    private fun failPaymentAndRelease(payment: BookingPayment, now: Instant) {
        val wasPending = payment.status == BookingPaymentStatus.CREATING_ORDER ||
            payment.status == BookingPaymentStatus.PENDING
        payment.fail(now)
        payment.reservationId?.let(reservationRepository::findForUpdate)?.release(now)
        if (wasPending) {
            outboxWriter.append(
                payment.bookingId,
                "WalletPaymentFailed",
                mapOf("bookingId" to payment.bookingId, "paymentId" to payment.id),
                now,
            )
        }
    }

    private fun appendLedger(
        walletId: UUID,
        type: LedgerEntryType,
        amountMinor: Long,
        referenceType: String,
        referenceId: String,
        idempotencyKey: String,
        description: String,
        now: Instant,
    ) {
        if (ledgerRepository.existsByIdempotencyKey(idempotencyKey)) return
        ledgerRepository.save(
            WalletLedgerEntry(
                id = UUID.randomUUID(),
                walletId = walletId,
                entryType = type,
                amountMinor = amountMinor,
                referenceType = referenceType,
                referenceId = referenceId,
                idempotencyKey = idempotencyKey,
                description = description,
                occurredAt = now,
            ),
        )
    }

    private fun paymentSnapshot(payment: BookingPayment): BookingPaymentSnapshot = BookingPaymentSnapshot(
        paymentId = payment.id,
        bookingId = payment.bookingId,
        walletId = payment.walletId,
        totalAmountMinor = payment.totalAmountMinor,
        walletAmountMinor = payment.walletAmountMinor,
        onlineAmountMinor = payment.onlineAmountMinor,
        status = payment.status.name,
        providerOrderId = payment.providerOrderId,
        reservationExpiresAt = payment.reservationId
            ?.let(reservationRepository::findById)
            ?.orElse(null)
            ?.expiresAt,
    )

    private fun topUpSnapshot(topUp: TopUpOrder) = TopUpSnapshot(
        topUpId = topUp.id,
        walletId = topUp.walletId,
        amountMinor = topUp.amountMinor,
        status = topUp.status.name,
        providerOrderId = topUp.providerOrderId,
    )

    private fun plusPurchaseSnapshot(purchase: PlusPurchase) = PlusPurchaseSnapshot(
        purchase.id,
        purchase.walletId,
        purchase.customerId,
        purchase.planCode,
        purchase.amountMinor,
        purchase.status.name,
        purchase.providerOrderId,
    )

    private fun refundSnapshot(refund: PaymentRefund) = RefundSnapshot(
        refundId = refund.id,
        bookingPaymentId = refund.bookingPaymentId,
        status = refund.status.name,
        walletAmountMinor = refund.walletAmountMinor,
        onlineAmountMinor = refund.onlineAmountMinor,
        providerRefundId = refund.providerRefundId,
    )

    private fun validateAmount(amountMinor: Long) {
        if (amountMinor < 100 || amountMinor % 100 != 0L) {
            throw WalletRuleViolation("Amount must be at least one rupee and use whole-rupee increments")
        }
    }

    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > 180) throw WalletRuleViolation("A valid idempotency key is required")
    }
}
