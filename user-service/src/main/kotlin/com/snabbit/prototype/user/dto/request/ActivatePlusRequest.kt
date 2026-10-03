package com.snabbit.prototype.user.dto.request

import com.snabbit.prototype.user.support.CustomerAccessDenied
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

data class ActivatePlusRequest(
    @field:NotBlank @field:Size(max = 120) val sourceReference: String,
    val startsAt: Instant,
    val expiresAt: Instant,
    @field:Min(0) @field:Max(20) val passCount: Int,
    @field:Min(0) val passDiscountMinor: Long,
    @field:Size(max = 60) val serviceCode: String? = null,
)
