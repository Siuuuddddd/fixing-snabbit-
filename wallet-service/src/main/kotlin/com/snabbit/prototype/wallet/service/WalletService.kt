package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.booking.BookingQuoteClient
import com.snabbit.prototype.wallet.payment.PaymentProvider
import com.snabbit.prototype.wallet.payment.PaymentProviderException
import com.snabbit.prototype.wallet.payment.ProviderOrder
import com.snabbit.prototype.wallet.support.PaymentConflict
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class WalletService(
    private val transactions: WalletTransactionService,
    private val provider: PaymentProvider,
    private val bookingQuotes: BookingQuoteClient,
    private val clock: Clock,
) {
    fun balance(walletId: UUID): WalletBalance = transactions.balance(walletId)

    fun ownership(walletId: UUID): WalletOwnershipSnapshot = transactions.ownership(walletId)

    fun balanceForCustomer(walletId: UUID, customerId: UUID): WalletBalance {
        transactions.requireOwnership(walletId, customerId)
        return transactions.balance(walletId)
    }

    fun addMoney(walletId: UUID, amountMinor: Long, idempotencyKey: String): TopUpSnapshot {
        val prepared = transactions.prepareTopUp(walletId, amountMinor, idempotencyKey)
        if (prepared.status != "CREATING_ORDER") return prepared
        val order = createOrRecoverOrder(
            amountMinor,
            orderReceipt("topup", prepared.topUpId),
        )
        return transactions.attachTopUpOrder(prepared.topUpId, order)
    }

    fun addMoneyForCustomer(
        walletId: UUID,
        customerId: UUID,
        amountMinor: Long,
        idempotencyKey: String,
    ): TopUpSnapshot {
        transactions.requireOwnership(walletId, customerId)
        return addMoney(walletId, amountMinor, idempotencyKey)
    }

    fun createPlusPurchase(
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
        val prepared = transactions.preparePlusPurchase(
            walletId, customerId, planCode, amountMinor, durationDays,
            passCount, passDiscountMinor, serviceCode, idempotencyKey,
        )
        if (prepared.status != "CREATING_ORDER") return prepared
        val order = createOrRecoverOrder(
            amountMinor,
            orderReceipt("plus", prepared.purchaseId),
        )
        return transactions.attachPlusPurchaseOrder(prepared.purchaseId, order)
    }

    fun createBookingPayment(
        walletId: UUID,
        bookingId: UUID,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPaymentSnapshot {
        transactions.existingBookingPayment(walletId, bookingId, idempotencyKey)?.let { return it }
        val quote = bookingQuotes.get(bookingId)
        if (quote.status != "AWAITING_PAYMENT") {
            throw PaymentConflict("Booking is not awaiting payment")
        }
        if (!quote.holdExpiresAt.isAfter(clock.instant())) {
            throw PaymentConflict("Booking slot hold has expired")
        }
        if (quote.currency != "INR" || quote.quotedTotalMinor <= 0) {
            throw PaymentConflict("Booking has no valid INR payment quote")
        }
        val prepared = transactions.prepareBookingPayment(
            walletId,
            bookingId,
            quote.customerId,
            quote.quotedTotalMinor,
            useWallet,
            idempotencyKey,
        )
        if (prepared.status != "CREATING_ORDER" || prepared.onlineAmountMinor == 0L) return prepared
        val order = createOrRecoverOrder(
            prepared.onlineAmountMinor,
            orderReceipt("booking", bookingId),
        )
        return transactions.attachBookingProviderOrder(prepared.paymentId, order)
    }

    fun refund(paymentId: UUID, idempotencyKey: String): RefundSnapshot {
        val prepared = transactions.prepareFullRefund(paymentId, idempotencyKey)
        if (prepared.snapshot.status == "COMPLETED") return prepared.snapshot
        return executeRefund(prepared.snapshot.refundId)
    }

    fun cancelBookingPayment(paymentId: UUID): BookingPaymentSnapshot =
        transactions.cancelBookingPayment(paymentId)

    fun executeRefund(refundId: UUID): RefundSnapshot {
        val execution = transactions.claimRefund(refundId) ?: return transactions.refund(refundId).also { current ->
            if (current.status == "PROCESSING") {
                throw PaymentConflict("Refund is already being processed")
            }
        }
        return try {
            val providerRefund = if (execution.onlineAmountMinor > 0) {
                val paymentId = execution.providerPaymentId
                    ?: throw PaymentConflict("Provider payment ID is missing for online refund")
                provider.refund(paymentId, execution.onlineAmountMinor, refundId.toString())
            } else {
                null
            }
            transactions.completeRefund(refundId, providerRefund)
        } catch (error: Exception) {
            transactions.retryRefund(refundId)
            if (error is PaymentProviderException || error is PaymentConflict) throw error
            throw PaymentProviderException("Refund processing failed", error)
        }
    }

    private fun createOrRecoverOrder(
        amountMinor: Long,
        receipt: String,
    ): ProviderOrder {
        provider.findOrderByReceipt(receipt)?.let { return it }
        return try {
            provider.createOrder(amountMinor, receipt)
        } catch (creationError: Exception) {
            val recovered = try {
                provider.findOrderByReceipt(receipt)
            } catch (reconciliationError: Exception) {
                creationError.addSuppressed(reconciliationError)
                throw creationError
            }
            if (recovered != null) return recovered
            throw creationError
        }
    }

    private fun orderReceipt(kind: String, id: UUID): String =
        "${kind}_${id.toString().replace("-", "")}".also { receipt ->
            check(receipt.length <= 40) { "Provider receipt exceeds Razorpay's 40-character limit" }
        }
}
