package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.domain.FinanceRefundAction
import com.snabbit.prototype.wallet.domain.FinanceRefundActionStatus
import com.snabbit.prototype.wallet.repository.FinanceRefundActionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class FinanceRefundAuditServiceTest {
    private val repository = mockk<FinanceRefundActionRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = FinanceRefundAuditService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `start persists staff identity and immutable refund target`() {
        val saved = slot<FinanceRefundAction>()
        val refundId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val actionId = service.start(refundId, staffId)

        assertEquals(actionId, saved.captured.id)
        assertEquals(refundId, saved.captured.refundId)
        assertEquals(staffId, saved.captured.staffId)
        assertEquals("RETRY", saved.captured.action)
        assertEquals(FinanceRefundActionStatus.REQUESTED, saved.captured.status)
        assertNull(saved.captured.failureReason)
    }

    @Test
    fun `failure reason is bounded for the audit row`() {
        val action = action()
        every { repository.findById(action.id) } returns Optional.of(action)

        service.fail(action.id, IllegalStateException("x".repeat(700)))

        assertEquals(FinanceRefundActionStatus.FAILED, action.status)
        assertEquals(500, action.failureReason?.length)
        assertEquals(now, action.updatedAt)
    }

    private fun action() = FinanceRefundAction(
        id = UUID.randomUUID(),
        refundId = UUID.randomUUID(),
        staffId = UUID.randomUUID(),
        action = "RETRY",
        status = FinanceRefundActionStatus.REQUESTED,
        createdAt = now,
        updatedAt = now.minusSeconds(1),
    )
}
