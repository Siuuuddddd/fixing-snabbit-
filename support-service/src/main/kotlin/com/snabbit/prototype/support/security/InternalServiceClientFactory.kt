package com.snabbit.prototype.support.security

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class InternalServiceClientFactory(
    private val builder: RestClient.Builder,
    private val properties: ServiceAuthProperties,
) {
    fun create(baseUrl: String): RestClient = builder.clone()
        .baseUrl(baseUrl)
        .defaultHeader(ServiceAuthWebFilter.HEADER, properties.token)
        .build()
}
