package com.snabbit.prototype.wallet.application

import com.snabbit.prototype.wallet.payment.PaymentProvider
import com.snabbit.prototype.wallet.payment.PaymentProviderException
import com.snabbit.prototype.wallet.support.PaymentConflict
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WalletService(
    private val transactions: WalletTransactionService,
    private val provider: PaymentProvider,
) {
    fun balance(walletId: UUID): WalletBalance = transactions.balance(walletId)

    fun addMoney(walletId: UUID, amountMinor: Long, idempotencyKey: String): TopUpSnapshot {
        val prepared = transactions.prepareTopUp(walletId, amountMinor, idempotencyKey)
        if (prepared.status != "CREATING_ORDER") return prepared
        return try {
            val order = provider.createOrder(amountMinor, "topup_${prepared.topUpId}")
            transactions.attachTopUpOrder(prepared.topUpId, order)
        } catch (error: Exception) {
            transactions.failTopUp(prepared.topUpId)
            throw error
        }
    }

    fun createBookingPayment(
        walletId: UUID,
        bookingId: UUID,
        totalAmountMinor: Long,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPaymentSnapshot {
        val prepared = transactions.prepareBookingPayment(
            walletId,
            bookingId,
            totalAmountMinor,
            useWallet,
            idempotencyKey,
        )
        if (prepared.status != "CREATING_ORDER" || prepared.onlineAmountMinor == 0L) return prepared
        return try {
            val order = provider.createOrder(prepared.onlineAmountMinor, "booking_$bookingId")
            transactions.attachBookingProviderOrder(prepared.paymentId, order)
        } catch (error: Exception) {
            transactions.failBookingPayment(prepared.paymentId)
            throw error
        }
    }

    fun refund(paymentId: UUID, idempotencyKey: String): RefundSnapshot {
        val prepared = transactions.prepareFullRefund(paymentId, idempotencyKey)
        if (prepared.snapshot.status == "COMPLETED") return prepared.snapshot
        return executeRefund(prepared.snapshot.refundId)
    }

    fun cancelBookingPayment(paymentId: UUID): BookingPaymentSnapshot =
        transactions.cancelBookingPayment(paymentId)

    fun executeRefund(refundId: UUID): RefundSnapshot {
        val execution = transactions.claimRefund(refundId) ?: return transactions.refund(refundId)
        return try {
            val providerRefund = if (execution.onlineAmountMinor > 0) {
                val paymentId = execution.providerPaymentId
                    ?: throw PaymentConflict("Provider payment ID is missing for online refund")
                provider.refund(paymentId, execution.onlineAmountMinor)
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
}
