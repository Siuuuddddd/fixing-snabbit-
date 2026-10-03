package com.snabbit.prototype.support.repository

import com.snabbit.prototype.support.domain.SupportMessage
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface SupportMessageRepository : JpaRepository<SupportMessage, Long> {
    fun findTop100ByTicketIdAndIdGreaterThanOrderByIdAsc(ticketId: UUID, afterId: Long): List<SupportMessage>
}
