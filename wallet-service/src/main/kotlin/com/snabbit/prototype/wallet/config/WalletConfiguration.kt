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
    val razorpay: RazorpayProperties,
)

data class RazorpayProperties(
    val mode: String,
    val baseUrl: String,
    val keyId: String,
    val keySecret: String,
    val webhookSecret: String,
)

@Configuration
class WalletConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
