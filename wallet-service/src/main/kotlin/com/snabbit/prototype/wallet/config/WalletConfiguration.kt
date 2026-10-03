package com.snabbit.prototype.wallet.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration
class WalletConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
