package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.request.WorkerLocationCommand
import com.snabbit.prototype.worker.dto.request.WorkerLocationRequest
import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot
import com.snabbit.prototype.worker.service.WorkerLocationService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/workers/me")
class WorkerLocationController(private val locations: WorkerLocationService) {
    @PostMapping("/location")
    fun updateLocation(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @Valid @RequestBody request: WorkerLocationRequest,
    ): WorkerLocationSnapshot = locations.update(
        workerId,
        WorkerLocationCommand(request.latitude, request.longitude, request.accuracyMeters, request.capturedAt),
    )
}
