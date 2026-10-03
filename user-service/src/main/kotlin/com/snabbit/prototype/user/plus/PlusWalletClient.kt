package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.user.dto.request.CreatePlusWalletPurchaseRequest

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

interface PlusWalletClient {
    fun create(walletId: UUID, idempotencyKey: String, request: CreatePlusWalletPurchaseRequest): PlusPurchaseSnapshot
}
