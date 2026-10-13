package com.snabbit.prototype.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("arrival-otp-provider")
data class ArrivalOtpProviderProperties(
    val provider: String = "stub",
    val baseUrl: String = "https://verify.twilio.com",
    val accountSid: String = "",
    val authToken: String = "",
    val apiKeySid: String = "",
    val apiKeySecret: String = "",
    val verifyServiceSid: String = "",
)
