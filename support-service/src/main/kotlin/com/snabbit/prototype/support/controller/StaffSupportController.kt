package com.snabbit.prototype.support.controller

import com.snabbit.prototype.support.domain.enums.SupportTicketStatus
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
@RequestMapping("/api/v1/staff/support")
class StaffSupportController(private val support: SupportService) {
    @GetMapping("/tickets")
    suspend fun tickets(
        @RequestParam(defaultValue = "OPEN") status: SupportTicketStatus,
    ): List<SupportTicketSnapshot> = withContext(Dispatchers.IO) { support.agentTickets(status) }

    @GetMapping("/tickets/{ticketId}/messages")
    suspend fun messages(
        @PathVariable ticketId: UUID,
        @RequestParam(defaultValue = "0") afterMessageId: Long,
    ): List<SupportMessageSnapshot> = withContext(Dispatchers.IO) {
        support.agentMessages(ticketId, afterMessageId)
    }

    @GetMapping("/tickets/{ticketId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable ticketId: UUID,
        @RequestParam(defaultValue = "0") afterMessageId: Long,
    ): Flow<SupportMessageSnapshot> = support.agentStream(ticketId, afterMessageId)

    @PostMapping("/tickets/{ticketId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun send(
        @PathVariable ticketId: UUID,
        @RequestHeader("X-Authenticated-Staff-Id") agentId: UUID,
        @Valid @RequestBody request: SendSupportMessageRequest,
    ): SupportMessageSnapshot = withContext(Dispatchers.IO) {
        support.sendAgentMessage(ticketId, agentId, request.body)
    }

    @PostMapping("/tickets/{ticketId}/resolve")
    suspend fun resolve(@PathVariable ticketId: UUID): SupportTicketSnapshot = withContext(Dispatchers.IO) {
        support.resolve(ticketId)
    }
}
