package com.snabbit.prototype.platform

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

interface SmsGateway {
    fun send(phoneE164: String, body: String)
}

@ConfigurationProperties("sms")
data class SmsProperties(
    val provider: String = "twilio",
    val baseUrl: String = "https://api.twilio.com",
    val accountSid: String = "",
    val authToken: String = "",
    val fromNumber: String = "",
)

class TwilioSmsGateway(
    restClientBuilder: RestClient.Builder,
    private val properties: SmsProperties,
) : SmsGateway {
    private val client = restClientBuilder
        .baseUrl(properties.baseUrl)
        .defaultHeaders { it.setBasicAuth(properties.accountSid, properties.authToken) }
        .build()

    init {
        require(properties.provider.equals("twilio", ignoreCase = true)) { "SMS provider must be twilio" }
        require(properties.accountSid.isNotBlank()) { "Twilio account SID is required" }
        require(properties.authToken.isNotBlank()) { "Twilio auth token is required" }
        require(properties.fromNumber.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            "Twilio from number must use E.164 format"
        }
    }

    override fun send(phoneE164: String, body: String) {
        require(phoneE164.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) { "SMS recipient must use E.164 format" }
        require(body.isNotBlank()) { "SMS body cannot be blank" }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("To", phoneE164)
            add("From", properties.fromNumber)
            add("Body", body)
        }
        client.post()
            .uri("/2010-04-01/Accounts/{accountSid}/Messages.json", properties.accountSid)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity()
    }
}

@AutoConfiguration
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "sms", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(SmsProperties::class)
class SmsGatewayAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder::class)
    fun providerRestClientBuilder(): RestClient.Builder = RestClient.builder()

    @Bean
    fun smsGateway(builder: RestClient.Builder, properties: SmsProperties): SmsGateway =
        TwilioSmsGateway(builder, properties)
}
