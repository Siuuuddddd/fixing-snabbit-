package com.snabbit.prototype.support.security

import java.nio.charset.StandardCharsets
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("service-auth")
data class ServiceAuthProperties(val token: String) {
    init {
        require(token.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "Service authentication token must contain at least 32 bytes"
        }
    }
}
