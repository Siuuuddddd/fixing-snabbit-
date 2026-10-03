package com.snabbit.prototype.user.dto.request

import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import com.snabbit.prototype.user.support.CustomerAccessDenied
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration
import java.util.UUID

data class CreatePlusWalletPurchaseRequest(
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String?,
)
