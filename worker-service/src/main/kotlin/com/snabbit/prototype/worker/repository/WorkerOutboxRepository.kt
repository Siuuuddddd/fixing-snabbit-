package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkerOutboxRepository : JpaRepository<WorkerOutboxEvent, UUID>
