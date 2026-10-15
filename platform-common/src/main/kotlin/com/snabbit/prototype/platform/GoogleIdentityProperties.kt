package com.snabbit.prototype.platform

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.google")
data class GoogleIdentityProperties(
    val verifier: String = "google",
    val customerClientIds: List<String> = emptyList(),
    val workerClientIds: List<String> = emptyList(),
    val jwkSetUri: String = "https://www.googleapis.com/oauth2/v3/certs",
    val issuers: Set<String> = setOf("accounts.google.com", "https://accounts.google.com"),
) {
    fun customerAudiences(): Set<String> = customerClientIds.normalized()

    fun workerAudiences(): Set<String> = workerClientIds.normalized()

    private fun List<String>.normalized(): Set<String> = asSequence()
        .flatMap { value -> value.split(',').asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}
