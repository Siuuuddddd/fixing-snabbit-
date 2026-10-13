package com.snabbit.prototype.booking.otp

import java.util.UUID

class StubArrivalOtpProvider : ArrivalOtpProvider {
    override fun start(phoneE164: String): ArrivalOtpStartResult =
        ArrivalOtpStartResult("stub:${UUID.randomUUID()}")

    override fun check(phoneE164: String, code: String): ArrivalOtpCheckResult =
        if (code == "000000") ArrivalOtpCheckResult.APPROVED else ArrivalOtpCheckResult.WRONG_CODE
}
