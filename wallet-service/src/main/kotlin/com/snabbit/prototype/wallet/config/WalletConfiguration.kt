package com.snabbit.prototype.wallet.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WalletConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
