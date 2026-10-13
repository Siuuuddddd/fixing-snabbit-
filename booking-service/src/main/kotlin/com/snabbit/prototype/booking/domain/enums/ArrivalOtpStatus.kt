package com.snabbit.prototype.booking.domain.enums

enum class ArrivalOtpStatus(val displayText: String) {
    OTP_SENT("OTP sent to the customer"),
    DELIVERY_FAILED("OTP delivery failed"),
    LOCKED("OTP verification locked"),
    VERIFIED("OTP verified"),
}
