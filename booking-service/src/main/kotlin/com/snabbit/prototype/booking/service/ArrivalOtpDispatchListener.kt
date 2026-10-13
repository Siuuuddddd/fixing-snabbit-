package com.snabbit.prototype.booking.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ArrivalOtpDispatchListener(private val dispatcher: ArrivalOtpDispatchService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun dispatch(event: ArrivalChallengeCreatedEvent) {
        runCatching { dispatcher.dispatch(event.challengeId) }
            .onFailure { logger.warn("Arrival OTP dispatch failed for challenge {}", event.challengeId) }
    }
}
