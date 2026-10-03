package com.snabbit.prototype.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth")
data class GatewayAuthProperties(
    val issuer: String,
    val jwtSecret: String,
)
