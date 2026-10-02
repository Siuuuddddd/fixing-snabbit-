package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.domain.RefundStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FinanceRefundService(
    private val transactions: WalletTransactionService,
    private val walletService: WalletService,
    private val audit: FinanceRefundAuditService,
) {
    fun list(status: RefundStatus?): List<FinanceRefundSnapshot> = transactions.financeRefunds(status)

    fun retry(refundId: UUID, staffId: UUID): FinanceRefundRetryResult {
        val actionId = audit.start(refundId, staffId)
        return try {
            val refund = walletService.executeRefund(refundId)
            audit.succeed(actionId)
            FinanceRefundRetryResult(actionId, refund)
        } catch (error: Exception) {
            audit.fail(actionId, error)
            throw error
        }
    }
}
