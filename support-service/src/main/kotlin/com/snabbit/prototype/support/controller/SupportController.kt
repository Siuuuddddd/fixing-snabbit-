package com.snabbit.prototype.support.controller

import com.snabbit.prototype.support.service.SupportMessageSnapshot
import com.snabbit.prototype.support.service.SupportRequestRejected
import com.snabbit.prototype.support.service.SupportResourceNotFound
import com.snabbit.prototype.support.service.SupportService
import com.snabbit.prototype.support.service.SupportTicketSnapshot
import com.snabbit.prototype.support.domain.SupportTicketStatus
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

data class OpenSupportTicketRequest(
    val bookingId: UUID,
    @field:NotBlank @field:Size(max = 160)
    val subject: String,
    @field:NotBlank @field:Size(max = 2_000)
    val initialMessage: String,
)

data class SendSupportMessageRequest(
    @field:NotBlank @field:Size(max = 2_000)
    val body: String,
)

data class SendAgentMessageRequest(
    val agentId: UUID,
    @field:NotBlank @field:Size(max = 2_000)
    val body: String,
)

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

@RestControllerAdvice
class SupportExceptionHandler {
    @ExceptionHandler(SupportResourceNotFound::class)
    fun notFound(error: SupportResourceNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(SupportRequestRejected::class)
    fun rejected(error: SupportRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
