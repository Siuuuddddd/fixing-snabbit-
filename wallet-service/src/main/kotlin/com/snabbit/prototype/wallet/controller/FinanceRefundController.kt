package com.snabbit.prototype.wallet.controller

import com.snabbit.prototype.wallet.domain.enums.RefundStatus
import com.snabbit.prototype.wallet.dto.response.FinanceRefundRetryResult
import com.snabbit.prototype.wallet.dto.response.FinanceRefundSnapshot
import com.snabbit.prototype.wallet.service.FinanceRefundService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
