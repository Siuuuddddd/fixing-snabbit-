package com.snabbit.prototype.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dev-seed")
data class BookingDevSeedProperties(
    val enabled: Boolean = false,
    val bookingWindowDays: Int = 10,
    val marginDays: Int = 2,
    val autoAcceptOffers: Boolean = false,
    val autoAcceptDelayMs: Long = 1_500,
    val autoProgress: Boolean = false,
    val autoProgressEnRouteDelayMs: Long = 1_500,
    val autoProgressArrivalDelayMs: Long = 3_000,
    val automationScanDelayMs: Long = 500,
) {
    init {
        require(bookingWindowDays == 10) { "The development seed booking window must remain 10 days" }
        require(marginDays >= 1) { "The development seed margin must be positive" }
        require(autoAcceptDelayMs >= 0) { "The development auto-accept delay cannot be negative" }
        require(autoProgressEnRouteDelayMs >= 0) { "The development en-route delay cannot be negative" }
        require(autoProgressArrivalDelayMs >= autoProgressEnRouteDelayMs) {
            "The development arrival delay must not precede en route"
        }
        require(automationScanDelayMs > 0) { "The development automation scan delay must be positive" }
    }
}
