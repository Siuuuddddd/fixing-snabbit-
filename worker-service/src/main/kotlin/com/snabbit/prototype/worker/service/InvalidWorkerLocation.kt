package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerLocationPoint
import com.snabbit.prototype.worker.repository.WorkerLocationRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidWorkerLocation(message: String) : RuntimeException(message)
