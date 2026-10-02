package com.snabbit.prototype.notification.api

import com.snabbit.prototype.notification.application.DeviceSnapshot
import com.snabbit.prototype.notification.application.NotificationAccessDenied
import com.snabbit.prototype.notification.application.NotificationNotFound
import com.snabbit.prototype.notification.application.NotificationRequestRejected
import com.snabbit.prototype.notification.application.NotificationService
import com.snabbit.prototype.notification.application.NotificationSnapshot
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

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val service: NotificationService) {
    @GetMapping
    suspend fun history(
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @RequestParam(defaultValue = "0") afterNotificationId: Long,
    ): List<NotificationSnapshot> = withContext(Dispatchers.IO) {
        service.history(customerId, afterNotificationId)
    }

    @PostMapping("/{notificationId}/read")
    suspend fun markRead(
        @PathVariable notificationId: Long,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): NotificationSnapshot = withContext(Dispatchers.IO) { service.markRead(customerId, notificationId) }

    @PostMapping("/devices")
    suspend fun registerDevice(
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: RegisterDeviceRequest,
    ): DeviceSnapshot = withContext(Dispatchers.IO) { service.registerDevice(customerId, request.pushToken) }

    @DeleteMapping("/devices/{deviceId}")
    suspend fun unregisterDevice(
        @PathVariable deviceId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): DeviceSnapshot = withContext(Dispatchers.IO) { service.unregisterDevice(customerId, deviceId) }
}

@RestControllerAdvice
class NotificationExceptionHandler {
    @ExceptionHandler(NotificationNotFound::class)
    fun notFound(error: NotificationNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(NotificationAccessDenied::class)
    fun forbidden(error: NotificationAccessDenied): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty())

    @ExceptionHandler(NotificationRequestRejected::class)
    fun rejected(error: NotificationRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())
}
