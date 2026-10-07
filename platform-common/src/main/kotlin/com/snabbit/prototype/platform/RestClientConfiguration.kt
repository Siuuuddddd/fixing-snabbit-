package com.snabbit.prototype.platform

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.web.client.RestClient

@AutoConfiguration
class RestClientConfiguration {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean(RestClient.Builder::class)
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}
