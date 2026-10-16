package com.snabbit.prototype.platform

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment

@ConfigurationProperties("deployment.production")
data class ProductionGuardProperties(
    val requiredProperties: List<String> = emptyList(),
)

class ProductionConfigurationGuard(
    environment: Environment,
    properties: ProductionGuardProperties,
) {
    init {
        require(!environment.getProperty("dev-seed.enabled", Boolean::class.java, false)) {
            "Unsafe production configuration: dev-seed.enabled must be false"
        }
        require(!environment.getProperty("dev-seed.auto-accept-offers", Boolean::class.java, false)) {
            "Unsafe production configuration: dev-seed.auto-accept-offers must be false"
        }
        require(!environment.getProperty("dev-seed.auto-progress", Boolean::class.java, false)) {
            "Unsafe production configuration: dev-seed.auto-progress must be false"
        }
        require(environment.getProperty("dev-seed.worker-login-email").isNullOrBlank()) {
            "Unsafe production configuration: dev-seed.worker-login-email must be empty"
        }
        require(environment.getProperty("dev-seed.demo-customer-email").isNullOrBlank()) {
            "Unsafe production configuration: dev-seed.demo-customer-email must be empty"
        }
        require(environment.getProperty("dev-seed.demo-customer-phone").isNullOrBlank()) {
            "Unsafe production configuration: dev-seed.demo-customer-phone must be empty"
        }
        require(properties.requiredProperties.isNotEmpty()) {
            "deployment.production.required-properties must list this service's production settings"
        }
        val unsafe = properties.requiredProperties.mapNotNull { key ->
            val value = environment.getProperty(key)?.trim().orEmpty()
            when {
                value.isEmpty() -> "$key is missing"
                value.isUnsafeDevelopmentValue() -> "$key still uses a local-development value"
                key == "wallet.razorpay.mode" && !value.equals("live", ignoreCase = true) ->
                    "$key must be live"
                key == "geocoding.provider" && !value.equals("google", ignoreCase = true) ->
                    "$key must be google"
                key == "notifications.push.provider" && !value.equals("fcm", ignoreCase = true) ->
                    "$key must be fcm"
                key == "sms.provider" && !value.equals("twilio", ignoreCase = true) ->
                    "$key must be twilio"
                key == "otp.provider" && !value.equals("twilio-verify", ignoreCase = true) ->
                    "$key must be twilio-verify"
                key == "arrival-otp-provider.provider" && !value.equals("twilio-verify", ignoreCase = true) ->
                    "$key must be twilio-verify"
                key == "auth.google.verifier" && !value.equals("google", ignoreCase = true) ->
                    "$key must be google"
                key == "email-auth.sender" && !value.equals("smtp", ignoreCase = true) ->
                    "$key must be smtp"
                else -> null
            }
        }
        require(unsafe.isEmpty()) {
            "Unsafe production configuration: ${unsafe.joinToString("; ")}"
        }
    }

    private fun String.isUnsafeDevelopmentValue(): Boolean {
        val normalized = lowercase()
        return normalized.contains("localhost") ||
            normalized.contains("mailpit") ||
            normalized.contains("snabbit.local") ||
            normalized.contains("snabbit-local") ||
            normalized.contains("local-only") ||
            normalized.contains("local-webhook") ||
            normalized.contains("example.invalid")
    }
}

@AutoConfiguration
@Profile("production")
@EnableConfigurationProperties(ProductionGuardProperties::class)
class ProductionConfigurationGuardAutoConfiguration {
    @Bean
    fun productionConfigurationGuard(
        environment: Environment,
        properties: ProductionGuardProperties,
    ) = ProductionConfigurationGuard(environment, properties)
}
