package com.snabbit.prototype.wallet.controller

import com.snabbit.prototype.wallet.domain.RefundStatus
import com.snabbit.prototype.wallet.service.FinanceRefundRetryResult
import com.snabbit.prototype.wallet.service.FinanceRefundService
import com.snabbit.prototype.wallet.service.FinanceRefundSnapshot
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/staff/finance/refunds")
class FinanceRefundController(private val refunds: FinanceRefundService) {
    @GetMapping
    fun list(@RequestParam(required = false) status: RefundStatus?): List<FinanceRefundSnapshot> =
        refunds.list(status)

    @PostMapping("/{refundId}/retry")
    fun retry(
        @PathVariable refundId: UUID,
        @RequestHeader("X-Authenticated-Staff-Id") staffId: UUID,
    ): FinanceRefundRetryResult = refunds.retry(refundId, staffId)
}
