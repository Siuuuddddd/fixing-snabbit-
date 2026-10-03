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

interface SupportTicketRepository : JpaRepository<SupportTicket, UUID> {
    fun findByBookingId(bookingId: UUID): SupportTicket?
    fun findTop100ByStatusOrderByUpdatedAtDesc(status: SupportTicketStatus): List<SupportTicket>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from SupportTicket ticket where ticket.id = :id")
    fun findForUpdate(@Param("id") id: UUID): SupportTicket?
}
