package com.snabbit.prototype.booking.persistence

import com.snabbit.prototype.booking.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID>
