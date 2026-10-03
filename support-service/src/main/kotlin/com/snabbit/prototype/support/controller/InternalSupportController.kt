package com.snabbit.prototype.support.controller

import com.snabbit.prototype.support.dto.response.SupportTicketSnapshot

import com.snabbit.prototype.support.dto.response.SupportMessageSnapshot

import com.snabbit.prototype.support.dto.request.SendAgentMessageRequest

import com.snabbit.prototype.support.domain.enums.SupportTicketStatus

import com.snabbit.prototype.support.service.SupportRequestRejected
import com.snabbit.prototype.support.service.SupportResourceNotFound
import com.snabbit.prototype.support.service.SupportService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

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
