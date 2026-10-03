package com.snabbit.prototype.wallet.dto.request

import com.snabbit.prototype.wallet.dto.response.WalletOwnershipSnapshot

import com.snabbit.prototype.wallet.dto.response.WalletBalance

import com.snabbit.prototype.wallet.dto.response.TopUpSnapshot

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import com.snabbit.prototype.wallet.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.wallet.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.wallet.service.RazorpayWebhookService
import com.snabbit.prototype.wallet.service.WalletService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AddMoneyRequest(@field:Min(100) val amountMinor: Long)
