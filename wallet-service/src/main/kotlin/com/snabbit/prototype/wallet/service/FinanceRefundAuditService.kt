package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.domain.enums.FinanceRefundActionStatus

import com.snabbit.prototype.wallet.domain.FinanceRefundAction
import com.snabbit.prototype.wallet.repository.FinanceRefundActionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class FinanceRefundAuditService(
    private val repository: FinanceRefundActionRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(refundId: UUID, staffId: UUID): UUID {
        val now = clock.instant()
        val action = FinanceRefundAction(
            id = UUID.randomUUID(),
            refundId = refundId,
            staffId = staffId,
            action = "RETRY",
            status = FinanceRefundActionStatus.REQUESTED,
            createdAt = now,
            updatedAt = now,
        )
        return repository.save(action).id
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun succeed(actionId: UUID) {
        repository.findById(actionId).orElseThrow().succeed(clock.instant())
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fail(actionId: UUID, error: Throwable) {
        val reason = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
        repository.findById(actionId).orElseThrow().fail(reason, clock.instant())
    }
}
