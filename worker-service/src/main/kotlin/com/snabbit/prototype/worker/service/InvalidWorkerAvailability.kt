package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus

import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class InvalidWorkerAvailability(message: String) : RuntimeException(message)
