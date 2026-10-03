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

@ConfigurationProperties("notifications.push.fcm")
data class FcmProperties(
    val projectId: String,
    val serviceAccountFile: String,
    val baseUrl: String = "https://fcm.googleapis.com",
)

interface FcmClient {
    fun send(tokens: List<String>, title: String, body: String)
}

interface GoogleAccessTokenProvider {
    fun accessToken(): String
}

class ServiceAccountAccessTokenProvider(
    private val builder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    private val properties: FcmProperties,
    private val clock: Clock,
) : GoogleAccessTokenProvider {
    @Volatile private var cached: CachedToken? = null

    override fun accessToken(): String {
        val now = clock.instant()
        cached?.takeIf { it.expiresAt.isAfter(now.plusSeconds(60)) }?.let { return it.value }
        synchronized(this) {
            cached?.takeIf { it.expiresAt.isAfter(clock.instant().plusSeconds(60)) }?.let { return it.value }
            val serviceAccount = objectMapper.readTree(Files.readString(Path.of(properties.serviceAccountFile)))
            val tokenUri = serviceAccount.requiredText("token_uri")
            val issuedAt = clock.instant()
            val assertion = signedAssertion(serviceAccount, issuedAt, tokenUri)
            val form = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                add("assertion", assertion)
            }
            val response = builder.build().post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode::class.java)
                ?: error("Google OAuth returned an empty response")
            val token = response.requiredText("access_token")
            val expiresIn = response.path("expires_in").asLong(3600)
            return token.also { cached = CachedToken(it, issuedAt.plusSeconds(expiresIn)) }
        }
    }

    private fun signedAssertion(serviceAccount: JsonNode, issuedAt: Instant, audience: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".toByteArray())
        val claims = objectMapper.writeValueAsBytes(
            mapOf(
                "iss" to serviceAccount.requiredText("client_email"),
                "scope" to "https://www.googleapis.com/auth/firebase.messaging",
                "aud" to audience,
                "iat" to issuedAt.epochSecond,
                "exp" to issuedAt.plusSeconds(3600).epochSecond,
            ),
        )
        val payload = encoder.encodeToString(claims)
        val unsigned = "$header.$payload"
        val pem = serviceAccount.requiredText("private_key")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)),
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key)
        signer.update(unsigned.toByteArray(StandardCharsets.US_ASCII))
        return "$unsigned.${encoder.encodeToString(signer.sign())}"
    }

    private fun JsonNode.requiredText(field: String): String = path(field).asText().takeIf(String::isNotBlank)
        ?: error("FCM service account is missing $field")

    private data class CachedToken(val value: String, val expiresAt: Instant)
}

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

@Component
@Profile("!local & !test")
class FcmPushDeliveryAdapter(private val client: FcmClient) : PushDeliveryAdapter {
    override fun send(tokens: List<String>, title: String, body: String) = client.send(tokens, title, body)
}

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
