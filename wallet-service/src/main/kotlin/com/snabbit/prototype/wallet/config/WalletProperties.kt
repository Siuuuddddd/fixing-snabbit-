package com.snabbit.prototype.wallet.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

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
