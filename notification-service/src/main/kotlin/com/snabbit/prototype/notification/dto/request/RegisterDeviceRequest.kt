package com.snabbit.prototype.notification.dto.request

import com.snabbit.prototype.notification.dto.response.NotificationSnapshot

import com.snabbit.prototype.notification.dto.response.DeviceSnapshot

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.snabbit.prototype.notification.service.NotificationAccessDenied
import com.snabbit.prototype.notification.service.NotificationNotFound
import com.snabbit.prototype.notification.service.NotificationRequestRejected
import com.snabbit.prototype.notification.service.NotificationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

data class RegisterDeviceRequest(
    @field:NotBlank @field:Size(min = 20, max = 512)
    val pushToken: String,
)
