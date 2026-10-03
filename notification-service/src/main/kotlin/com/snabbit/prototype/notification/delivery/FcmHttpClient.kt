package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

class FcmHttpClient(
    builder: RestClient.Builder,
    private val tokens: GoogleAccessTokenProvider,
    private val properties: FcmProperties,
) : FcmClient {
    private val client = builder.baseUrl(properties.baseUrl).build()

    override fun send(tokens: List<String>, title: String, body: String) {
        require(tokens.isNotEmpty()) { "Recipient has no active push device" }
        tokens.forEach { token ->
            client.post()
                .uri("/v1/projects/{projectId}/messages:send", properties.projectId)
                .headers { it.setBearerAuth(this.tokens.accessToken()) }
                .body(
                    mapOf(
                        "message" to mapOf(
                            "token" to token,
                            "notification" to mapOf("title" to title, "body" to body),
                        ),
                    ),
                )
                .retrieve()
                .toBodilessEntity()
        }
    }
}
