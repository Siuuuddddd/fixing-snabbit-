package com.snabbit.prototype.wallet.dto.request

import jakarta.validation.constraints.Min

data class AddMoneyRequest(@field:Min(100) val amountMinor: Long)
