package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.response.WorkerAvailabilitySnapshot

import com.snabbit.prototype.worker.dto.request.WorkerAvailabilityRequest

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

@RestController
@RequestMapping("/api/v1/workers/me/availability")
class WorkerAvailabilityController(private val availability: WorkerAvailabilityService) {
    @GetMapping
    fun list(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
    ): List<WorkerAvailabilitySnapshot> = availability.list(workerId, from, to)

    @PutMapping("/{serviceDate}")
    fun update(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @PathVariable serviceDate: LocalDate,
        @Valid @RequestBody request: WorkerAvailabilityRequest,
    ): WorkerAvailabilitySnapshot = availability.update(
        workerId,
        serviceDate,
        WorkerAvailabilityCommand(request.status, request.availableFrom, request.availableUntil),
    )
}
