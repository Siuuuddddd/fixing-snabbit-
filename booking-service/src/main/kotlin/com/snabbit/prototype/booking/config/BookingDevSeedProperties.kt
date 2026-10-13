package com.snabbit.prototype.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dev-seed")
data class BookingDevSeedProperties(
    val enabled: Boolean = false,
    val bookingWindowDays: Int = 10,
    val marginDays: Int = 2,
) {
    init {
        require(bookingWindowDays == 10) { "The development seed booking window must remain 10 days" }
        require(marginDays >= 1) { "The development seed margin must be positive" }
    }
}
