package com.snabbit.prototype.booking.otp

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.config.ArrivalOtpProviderProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class ArrivalOtpProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean(ArrivalOtpProvider::class)
    @ConditionalOnProperty(prefix = "arrival-otp-provider", name = ["provider"], havingValue = "twilio-verify")
    fun twilioArrivalOtpProvider(
        builder: RestClient.Builder,
        properties: ArrivalOtpProviderProperties,
        mapper: ObjectMapper,
    ): ArrivalOtpProvider = TwilioArrivalOtpProvider(builder, properties, mapper)

    @Bean
    @ConditionalOnMissingBean(ArrivalOtpProvider::class)
    @ConditionalOnProperty(prefix = "arrival-otp-provider", name = ["provider"], havingValue = "stub", matchIfMissing = true)
    fun stubArrivalOtpProvider(): ArrivalOtpProvider = StubArrivalOtpProvider()
}
