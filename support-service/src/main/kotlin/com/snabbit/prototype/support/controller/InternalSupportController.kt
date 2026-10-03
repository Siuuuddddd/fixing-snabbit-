package com.snabbit.prototype.support.controller

import com.snabbit.prototype.support.dto.request.SendAgentMessageRequest
import com.snabbit.prototype.support.dto.response.SupportMessageSnapshot
import com.snabbit.prototype.support.dto.response.SupportTicketSnapshot
import com.snabbit.prototype.support.service.SupportService
import jakarta.validation.Valid
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/support")
class InternalSupportController(private val support: SupportService) {
    @PostMapping("/tickets/{ticketId}/messages")
    suspend fun send(
        @PathVariable ticketId: UUID,
        @Valid @RequestBody request: SendAgentMessageRequest,
    ): SupportMessageSnapshot = withContext(Dispatchers.IO) {
        support.sendAgentMessage(ticketId, request.agentId, request.body)
    }

    @PostMapping("/tickets/{ticketId}/resolve")
    suspend fun resolve(@PathVariable ticketId: UUID): SupportTicketSnapshot = withContext(Dispatchers.IO) {
        support.resolve(ticketId)
    }
}
