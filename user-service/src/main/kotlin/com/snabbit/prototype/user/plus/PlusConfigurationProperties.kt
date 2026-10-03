package com.snabbit.prototype.user.plus

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("plus")
data class PlusConfigurationProperties(
    val reconciliationDelay: Duration = Duration.ofMinutes(1),
    val plans: Map<String, PlusPlanProperties> = emptyMap(),
)
