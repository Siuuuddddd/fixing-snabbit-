package com.snabbit.prototype.platform

import java.time.Clock
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(GoogleIdentityProperties::class)
class GoogleIdentityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(GoogleIdentityVerifier::class)
    @ConditionalOnProperty(prefix = "auth.google", name = ["verifier"], havingValue = "google")
    fun googleIdentityVerifier(
        properties: GoogleIdentityProperties,
        clock: Clock,
    ): GoogleIdentityVerifier = NimbusGoogleIdentityVerifier(properties, clock)
}
