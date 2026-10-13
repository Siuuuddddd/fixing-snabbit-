package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.request.PublicWorkerProfilesRequest
import com.snabbit.prototype.worker.dto.response.PublicWorkerProfile
import com.snabbit.prototype.worker.service.WorkerPublicProfileService
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class WorkerPublicProfileController(private val profiles: WorkerPublicProfileService) {
    @GetMapping("/api/v1/workers/{workerId}/profile")
    fun profile(@PathVariable workerId: UUID): ResponseEntity<PublicWorkerProfile> =
        profiles.profile(workerId)?.let(ResponseEntity<PublicWorkerProfile>::ok)
            ?: ResponseEntity.notFound().build()

    @PostMapping("/internal/v1/workers/public-profiles")
    fun profiles(@RequestBody request: PublicWorkerProfilesRequest): List<PublicWorkerProfile> =
        profiles.profiles(request.workerIds, request.customerId)
}
