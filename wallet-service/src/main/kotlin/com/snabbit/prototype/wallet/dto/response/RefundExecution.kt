package com.snabbit.prototype.wallet.dto.response

import com.snabbit.prototype.wallet.dto.response.ProviderRefund

import com.snabbit.prototype.wallet.dto.response.ProviderOrder

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

data class RefundExecution(
    val refundId: UUID,
    val providerPaymentId: String?,
    val onlineAmountMinor: Long,
)
