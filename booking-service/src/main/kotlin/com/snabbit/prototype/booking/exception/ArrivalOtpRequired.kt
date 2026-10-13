package com.snabbit.prototype.booking.exception

import java.util.UUID

class ArrivalOtpRequired(
    val verificationEndpoint: String,
) : RuntimeException("Arrival OTP verification is required before service can start") {
    constructor(bookingId: UUID, visitIndex: Int) : this(
        "/api/v1/worker/bookings/$bookingId/visits/$visitIndex/verify-arrival-otp",
    )
}
