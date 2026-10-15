package com.snabbit.prototype.worker.config

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dev-seed")
data class WorkerDevSeedProperties(
    val enabled: Boolean = false,
    val bookingWindowDays: Int = 10,
    val marginDays: Int = 2,
    val latitude: BigDecimal = BigDecimal("19.076090"),
    val longitude: BigDecimal = BigDecimal("72.877426"),
    val serviceRadiusKm: BigDecimal = BigDecimal("50.00"),
    val workerLoginEmail: String = "",
) {
    init {
        require(bookingWindowDays == 10) { "The development seed booking window must remain 10 days" }
        require(marginDays >= 1) { "The development seed margin must be positive" }
        require(serviceRadiusKm > BigDecimal.ZERO) { "The development seed service radius must be positive" }
        require(workerLoginEmail.isBlank() || workerLoginEmail.contains('@')) {
            "dev-seed.worker-login-email must be an email address"
        }
    }
}
