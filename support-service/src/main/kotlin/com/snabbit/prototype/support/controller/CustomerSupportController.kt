package com.snabbit.prototype.support.controller

import com.snabbit.prototype.support.dto.request.OpenSupportTicketRequest
import com.snabbit.prototype.support.dto.request.SendSupportMessageRequest
import com.snabbit.prototype.support.dto.response.SupportMessageSnapshot
import com.snabbit.prototype.support.dto.response.SupportTicketSnapshot
import com.snabbit.prototype.support.service.SupportService
import jakarta.validation.Valid
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/support")
class CustomerSupportController(private val support: SupportService) {
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun open(
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: OpenSupportTicketRequest,
    ): SupportTicketSnapshot = withContext(Dispatchers.IO) {
        support.open(customerId, request.bookingId, request.subject, request.initialMessage)
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun send(
        @PathVariable ticketId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: SendSupportMessageRequest,
    ): SupportMessageSnapshot = withContext(Dispatchers.IO) {
        support.sendCustomerMessage(ticketId, customerId, request.body)
    }

    @GetMapping("/tickets/{ticketId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable ticketId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @RequestParam(defaultValue = "0") afterMessageId: Long,
    ): Flow<SupportMessageSnapshot> = support.stream(ticketId, customerId, afterMessageId)
}
