package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.OutboxEvent
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID>
