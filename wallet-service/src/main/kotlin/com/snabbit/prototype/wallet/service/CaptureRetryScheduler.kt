package com.snabbit.prototype.wallet.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CaptureRetryScheduler(private val transactions: WalletTransactionService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${wallet.webhook-retry-delay}")
    fun retryPendingCaptures() {
        transactions.pendingCaptures().forEach { capture ->
            runCatching { transactions.processCapture(capture) }
                .onFailure { error ->
                    logger.warn("Capture webhook {} will be retried", capture.eventId, error)
                }
        }
    }
}
