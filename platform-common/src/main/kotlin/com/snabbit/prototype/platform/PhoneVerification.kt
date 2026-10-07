package com.snabbit.prototype.platform

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

enum class VerificationStatus {
    APPROVED,
    WRONG_CODE,
    EXPIRED,
    MAX_ATTEMPTS,
}

data class VerificationStartResult(
    val expiresInSeconds: Long,
    val debugCode: String? = null,
)

interface PhoneVerificationProvider {
    fun start(phoneE164: String): VerificationStartResult
    fun check(phoneE164: String, code: String): VerificationStatus
    fun clear(phoneE164: String) = Unit
}

class UnverifiedTrialRecipient : RuntimeException(
    "This Twilio trial can only send codes to verified recipient numbers. Verify this number in Twilio or upgrade the account.",
)

class VerificationProviderRateLimited : RuntimeException(
    "Too many verification attempts. Please wait before trying again.",
)

class VerificationProviderUnavailable : RuntimeException(
    "Phone verification is temporarily unavailable. Please try again.",
)

@ConfigurationProperties("otp")
data class OtpProviderProperties(
    val provider: String = "twilio-verify",
    val baseUrl: String = "https://verify.twilio.com",
    val accountSid: String = "",
    val authToken: String = "",
    val apiKeySid: String = "",
    val apiKeySecret: String = "",
    val verifyServiceSid: String = "",
    val ttl: Duration = Duration.ofMinutes(5),
    val exposeCode: Boolean = false,
)

class TwilioVerifyProvider(
    builder: RestClient.Builder,
    private val properties: OtpProviderProperties,
    private val objectMapper: ObjectMapper,
) : PhoneVerificationProvider {
    private val client: RestClient

    init {
        require(properties.accountSid.matches(Regex("^AC[0-9a-fA-F]{32}$"))) {
            "Twilio account SID is required"
        }
        val username = properties.apiKeySid.ifBlank { properties.accountSid }
        val password = properties.apiKeySecret.ifBlank { properties.authToken }
        require(password.isNotBlank()) { "Twilio auth token or API key secret is required" }
        require(properties.verifyServiceSid.matches(Regex("^VA[0-9a-fA-F]{32}$"))) {
            "Twilio Verify service SID is required"
        }
        client = builder
            .baseUrl(properties.baseUrl)
            .defaultHeaders { it.setBasicAuth(username, password) }
            .build()
    }

    override fun start(phoneE164: String): VerificationStartResult {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneE164)
            add("Channel", "sms")
        }
        val status = try {
            client.post()
                .uri("/v2/Services/{serviceSid}/Verifications", properties.verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?.let(::status)
        } catch (error: RestClientResponseException) {
            throw mapped(error, checking = false)
        } catch (error: Exception) {
            throw VerificationProviderUnavailable()
        }
        if (status != "pending") throw VerificationProviderUnavailable()
        return VerificationStartResult(properties.ttl.seconds)
    }

    override fun check(phoneE164: String, code: String): VerificationStatus {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneE164)
            add("Code", code)
        }
        val providerStatus = try {
            client.post()
                .uri("/v2/Services/{serviceSid}/VerificationCheck", properties.verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?.let(::status)
        } catch (error: RestClientResponseException) {
            val mapped = mapped(error, checking = true)
            if (mapped is VerificationExpiredMarker) return VerificationStatus.EXPIRED
            if (mapped is VerificationAttemptsMarker) return VerificationStatus.MAX_ATTEMPTS
            if (mapped is VerificationWrongCodeMarker) return VerificationStatus.WRONG_CODE
            throw mapped
        } catch (error: Exception) {
            throw VerificationProviderUnavailable()
        }
        return when (providerStatus) {
            "approved" -> VerificationStatus.APPROVED
            "pending" -> VerificationStatus.WRONG_CODE
            "expired", "canceled", "deleted" -> VerificationStatus.EXPIRED
            "max_attempts_reached" -> VerificationStatus.MAX_ATTEMPTS
            else -> throw VerificationProviderUnavailable()
        }
    }

    private fun status(body: String): String = runCatching {
        objectMapper.readTree(body).path("status").asText().lowercase()
    }.getOrDefault("")

    private fun mapped(error: RestClientResponseException, checking: Boolean): RuntimeException {
        val code = runCatching {
            objectMapper.readTree(error.responseBodyAsString).path("code").asInt()
        }.getOrDefault(0)
        return when {
            code in setOf(14111, 21608) -> UnverifiedTrialRecipient()
            code == 60202 -> VerificationAttemptsMarker()
            code in setOf(60203, 60207, 60212, 60624, 60626) -> VerificationProviderRateLimited()
            checking && (error.statusCode.value() == 404 || code in setOf(20404, 60623)) ->
                VerificationExpiredMarker()
            checking && code == 60200 -> VerificationWrongCodeMarker()
            else -> VerificationProviderUnavailable()
        }
    }

    private class VerificationExpiredMarker : RuntimeException()
    private class VerificationAttemptsMarker : RuntimeException()
    private class VerificationWrongCodeMarker : RuntimeException()
}

class LoggingPhoneVerificationProvider(
    private val properties: OtpProviderProperties,
    private val clock: Clock,
) : PhoneVerificationProvider {
    private data class Challenge(val hash: String, val expiresAt: Instant)

    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val random = SecureRandom()

    override fun start(phoneE164: String): VerificationStartResult {
        val code = "%06d".format(random.nextInt(1_000_000))
        challenges[phoneE164] = Challenge(hash(code), clock.instant().plus(properties.ttl))
        return VerificationStartResult(
            expiresInSeconds = properties.ttl.seconds,
            debugCode = code.takeIf { properties.exposeCode },
        )
    }

    override fun check(phoneE164: String, code: String): VerificationStatus {
        val challenge = challenges[phoneE164] ?: return VerificationStatus.EXPIRED
        if (!challenge.expiresAt.isAfter(clock.instant())) {
            challenges.remove(phoneE164)
            return VerificationStatus.EXPIRED
        }
        val approved = MessageDigest.isEqual(
            challenge.hash.toByteArray(),
            hash(code).toByteArray(),
        )
        if (approved) challenges.remove(phoneE164)
        return if (approved) VerificationStatus.APPROVED else VerificationStatus.WRONG_CODE
    }

    override fun clear(phoneE164: String) {
        challenges.remove(phoneE164)
    }

    private fun hash(code: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(code.toByteArray()),
    )
}

@AutoConfiguration(after = [RestClientConfiguration::class])
@EnableConfigurationProperties(OtpProviderProperties::class)
class PhoneVerificationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(PhoneVerificationProvider::class)
    @ConditionalOnProperty(prefix = "otp", name = ["provider"], havingValue = "twilio-verify")
    fun twilioVerifyProvider(
        builder: RestClient.Builder,
        properties: OtpProviderProperties,
        objectMapper: ObjectMapper,
    ): PhoneVerificationProvider = TwilioVerifyProvider(builder, properties, objectMapper)

    @Bean
    @ConditionalOnMissingBean(PhoneVerificationProvider::class)
    @ConditionalOnProperty(prefix = "otp", name = ["provider"], havingValue = "logging")
    fun loggingPhoneVerificationProvider(
        properties: OtpProviderProperties,
        clock: Clock,
    ): PhoneVerificationProvider = LoggingPhoneVerificationProvider(properties, clock)
}
