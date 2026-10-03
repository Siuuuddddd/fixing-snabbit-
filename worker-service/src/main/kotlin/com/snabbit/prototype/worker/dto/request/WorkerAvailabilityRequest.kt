package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.dto.response.WorkerAvailabilitySnapshot

import com.snabbit.prototype.worker.dto.request.WorkerAvailabilityCommand

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus

import com.snabbit.prototype.worker.service.WorkerAvailabilityService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class WorkerAvailabilityRequest(
    val status: AvailabilityStatus,
    val availableFrom: LocalTime? = null,
    val availableUntil: LocalTime? = null,
)
