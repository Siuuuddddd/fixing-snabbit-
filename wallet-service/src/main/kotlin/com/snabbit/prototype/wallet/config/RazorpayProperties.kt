package com.snabbit.prototype.wallet.config

data class RazorpayProperties(
    val mode: String,
    val baseUrl: String,
    val keyId: String,
    val keySecret: String,
    val webhookSecret: String,
) {
    init {
        require(mode.lowercase() in setOf("fake", "test", "live")) {
            "Razorpay mode must be fake, test or live"
        }
    }

    fun usesProviderApi(): Boolean = !mode.equals("fake", ignoreCase = true)
}
