package com.snabbit.prototype.support.service

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("support")
data class SupportProperties(val streamPollDelay: Duration)
