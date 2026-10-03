package com.snabbit.prototype.booking.domain.enums

enum class BookingStatus {
    AWAITING_WORKER,
    AWAITING_PAYMENT,
    CONFIRMED,
    WORKER_UNAVAILABLE,
    EXPIRED,
    CANCELLED,
    COMPLETED,
}
