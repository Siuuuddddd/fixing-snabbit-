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

@Configuration
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "notifications.push", name = ["provider"], havingValue = "fcm")
class FcmConfiguration {
    @Bean
    fun googleAccessTokenProvider(
        builder: RestClient.Builder,
        objectMapper: ObjectMapper,
        properties: FcmProperties,
        clock: Clock,
    ): GoogleAccessTokenProvider {
        require(properties.projectId.isNotBlank()) { "FCM project ID is required" }
        require(properties.serviceAccountFile.isNotBlank()) { "FCM service-account file is required" }
        return ServiceAccountAccessTokenProvider(builder, objectMapper, properties, clock)
    }

    @Bean
    fun fcmClient(
        builder: RestClient.Builder,
        tokens: GoogleAccessTokenProvider,
        properties: FcmProperties,
    ): FcmClient = FcmHttpClient(builder, tokens, properties)
}
