package com.snabbit.prototype.wallet.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@ConfigurationProperties("wallet")
data class WalletProperties(
    val currency: String,
    val reservationDuration: Duration,
    val refundRetryDelay: Duration,
    val refundProcessingLease: Duration,
    val razorpay: RazorpayProperties,
) {
    init {
        require(!reservationDuration.isZero && !reservationDuration.isNegative) {
            "Wallet reservation duration must be positive"
        }
        require(!refundRetryDelay.isZero && !refundRetryDelay.isNegative) {
            "Refund retry delay must be positive"
        }
        require(!refundProcessingLease.isZero && !refundProcessingLease.isNegative) {
            "Refund processing lease must be positive"
        }
    }
}
