package com.snabbit.prototype.booking.security

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class InternalServiceClientFactory(
    private val builder: RestClient.Builder,
    private val properties: ServiceAuthProperties,
) {
    fun create(baseUrl: String): RestClient = builder.clone()
        .baseUrl(baseUrl)
        .defaultHeader(ServiceAuthFilter.HEADER, properties.token)
        .build()
}
