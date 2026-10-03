package com.snabbit.prototype.support.repository

import com.snabbit.prototype.support.domain.enums.SupportTicketStatus

import com.snabbit.prototype.support.domain.SupportMessage
import com.snabbit.prototype.support.domain.SupportTicket
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SupportMessageRepository : JpaRepository<SupportMessage, Long> {
    fun findTop100ByTicketIdAndIdGreaterThanOrderByIdAsc(ticketId: UUID, afterId: Long): List<SupportMessage>
}
