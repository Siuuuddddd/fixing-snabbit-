package com.snabbit.prototype.booking.otp

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.config.ArrivalOtpProviderProperties
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

class TwilioArrivalOtpProvider(
    builder: RestClient.Builder,
    private val properties: ArrivalOtpProviderProperties,
    private val mapper: ObjectMapper,
) : ArrivalOtpProvider {
    private val client: RestClient

    init {
        require(properties.accountSid.matches(Regex("^AC[0-9a-fA-F]{32}$"))) { "Arrival Twilio account SID is required" }
        require(properties.verifyServiceSid.matches(Regex("^VA[0-9a-fA-F]{32}$"))) {
            "Arrival Twilio Verify service SID is required"
        }
        val username = properties.apiKeySid.ifBlank { properties.accountSid }
        val password = properties.apiKeySecret.ifBlank { properties.authToken }
        require(password.isNotBlank()) { "Arrival Twilio credentials are required" }
        client = builder.clone().baseUrl(properties.baseUrl)
            .defaultHeaders { it.setBasicAuth(username, password) }
            .build()
    }

    override fun start(phoneE164: String): ArrivalOtpStartResult {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneE164)
            add("Channel", "sms")
        }
        return try {
            val body = client.post()
                .uri("/v2/Services/{serviceSid}/Verifications", properties.verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?: throw ArrivalOtpProviderUnavailable()
            val node = mapper.readTree(body)
            if (node.path("status").asText().lowercase() != "pending") throw ArrivalOtpProviderUnavailable()
            val reference = node.path("sid").asText()
            if (reference.isBlank()) throw ArrivalOtpProviderUnavailable()
            ArrivalOtpStartResult(reference)
        } catch (_: ArrivalOtpProviderUnavailable) {
            throw ArrivalOtpProviderUnavailable()
        } catch (_: Exception) {
            throw ArrivalOtpProviderUnavailable()
        }
    }

    override fun check(phoneE164: String, code: String): ArrivalOtpCheckResult {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneE164)
            add("Code", code)
        }
        return try {
            val body = client.post()
                .uri("/v2/Services/{serviceSid}/VerificationCheck", properties.verifyServiceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?: throw ArrivalOtpProviderUnavailable()
            when (mapper.readTree(body).path("status").asText().lowercase()) {
                "approved" -> ArrivalOtpCheckResult.APPROVED
                "pending" -> ArrivalOtpCheckResult.WRONG_CODE
                "expired", "canceled", "deleted" -> ArrivalOtpCheckResult.EXPIRED
                "max_attempts_reached" -> ArrivalOtpCheckResult.LOCKED
                else -> throw ArrivalOtpProviderUnavailable()
            }
        } catch (_: ArrivalOtpProviderUnavailable) {
            throw ArrivalOtpProviderUnavailable()
        } catch (error: org.springframework.web.client.RestClientResponseException) {
            when (runCatching { mapper.readTree(error.responseBodyAsString).path("code").asInt() }.getOrDefault(0)) {
                60200 -> ArrivalOtpCheckResult.WRONG_CODE
                60202 -> ArrivalOtpCheckResult.LOCKED
                20404, 60623 -> ArrivalOtpCheckResult.EXPIRED
                else -> throw ArrivalOtpProviderUnavailable()
            }
        } catch (_: Exception) {
            throw ArrivalOtpProviderUnavailable()
        }
    }
}
