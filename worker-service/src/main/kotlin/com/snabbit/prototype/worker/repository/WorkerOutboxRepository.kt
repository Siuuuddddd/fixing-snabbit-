package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerOutboxEvent
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerOutboxRepository : JpaRepository<WorkerOutboxEvent, UUID>
