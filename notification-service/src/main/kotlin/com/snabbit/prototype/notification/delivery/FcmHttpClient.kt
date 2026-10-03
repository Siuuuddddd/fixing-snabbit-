package com.snabbit.prototype.notification.delivery

import org.springframework.web.client.RestClient

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
