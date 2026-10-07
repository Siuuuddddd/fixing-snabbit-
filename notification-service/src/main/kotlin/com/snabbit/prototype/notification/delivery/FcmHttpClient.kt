package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

class FcmHttpClient(
    builder: RestClient.Builder,
    private val tokens: GoogleAccessTokenProvider,
    private val properties: FcmProperties,
    private val objectMapper: ObjectMapper,
) : FcmClient {
    private val client = builder.baseUrl(properties.baseUrl).build()

    override fun send(tokens: List<String>, message: PushMessage): PushDeliveryResult {
        require(tokens.isNotEmpty()) { "Recipient has no active push device" }
        val invalidTokens = linkedSetOf<String>()
        tokens.forEach { token ->
            try {
                client.post()
                    .uri("/v1/projects/{projectId}/messages:send", properties.projectId)
                    .headers { it.setBearerAuth(this.tokens.accessToken()) }
                    .body(
                        mapOf(
                            "message" to mapOf(
                                "token" to token,
                                "notification" to mapOf("title" to message.title, "body" to message.body),
                                "data" to message.data,
                                "android" to mapOf(
                                    "priority" to "HIGH",
                                    "notification" to mapOf("channel_id" to message.channelId),
                                ),
                            ),
                        ),
                    )
                    .retrieve()
                    .toBodilessEntity()
            } catch (error: RestClientResponseException) {
                if (error.isInvalidRegistration()) invalidTokens += token else throw error
            }
        }
        return PushDeliveryResult(invalidTokens)
    }

    private fun RestClientResponseException.isInvalidRegistration(): Boolean = runCatching {
        val error = objectMapper.readTree(responseBodyAsString).path("error")
        val fcmCodes = error.path("details").mapNotNull { detail ->
            detail.path("errorCode").asText().takeIf(String::isNotBlank)
        }.toSet()
        "UNREGISTERED" in fcmCodes || "INVALID_ARGUMENT" in fcmCodes
    }.getOrDefault(false)
}
