package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.dto.response.WorkerMatch

import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot

import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot

import com.snabbit.prototype.worker.dto.request.WorkerLocationCommand

import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand

import com.snabbit.prototype.worker.service.InvalidMatchRequest
import com.snabbit.prototype.worker.service.WorkerMatchingService
import com.snabbit.prototype.worker.service.InvalidWorkerLocation
import com.snabbit.prototype.worker.service.InvalidWorkerAvailability
import com.snabbit.prototype.worker.service.WorkerLocationService
import com.snabbit.prototype.worker.service.WorkerDirectoryService
import com.snabbit.prototype.worker.auth.InvalidWorkerOtp
import com.snabbit.prototype.worker.auth.WorkerOtpRateLimited
import com.snabbit.prototype.worker.rating.WorkerRatingRejected
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class MatchWorkersRequest(
    @field:NotBlank
    val serviceCode: String,
    @field:Min(1)
    val basePricePerHourMinor: Long,
    val startsAt: Instant,
    @field:Min(60) @field:Max(240)
    val durationMinutes: Int,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceKm: Double = 15.0,
    val hasPets: Boolean = false,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val favouriteWorkerIds: List<UUID> = emptyList(),
    val plusActive: Boolean = false,
)
