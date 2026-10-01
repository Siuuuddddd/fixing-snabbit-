package com.snabbit.prototype.wallet.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RefundRetryScheduler(
    private val transactions: WalletTransactionService,
    private val walletService: WalletService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${wallet.refund-retry-delay}")
    fun retryPendingRefunds() {
        transactions.pendingRefundIds().forEach { refundId ->
            runCatching { walletService.executeRefund(refundId) }
                .onFailure { error -> logger.warn("Refund {} will be retried", refundId, error) }
        }
    }
}
