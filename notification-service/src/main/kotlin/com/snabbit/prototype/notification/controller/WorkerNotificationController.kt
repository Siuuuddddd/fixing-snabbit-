package com.snabbit.prototype.notification.controller

import com.snabbit.prototype.notification.dto.response.NotificationSnapshot

import com.snabbit.prototype.notification.dto.response.DeviceSnapshot

import com.snabbit.prototype.notification.dto.request.RegisterDeviceRequest

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

@RestController
@RequestMapping("/api/v1/worker/notifications")
class WorkerNotificationController(private val service: NotificationService) {
    @GetMapping
    suspend fun history(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @RequestParam(defaultValue = "0") afterNotificationId: Long,
    ): List<NotificationSnapshot> = withContext(Dispatchers.IO) {
        service.history(RecipientType.WORKER, workerId, afterNotificationId)
    }

    @PostMapping("/{notificationId}/read")
    suspend fun markRead(
        @PathVariable notificationId: Long,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): NotificationSnapshot = withContext(Dispatchers.IO) {
        service.markRead(RecipientType.WORKER, workerId, notificationId)
    }

    @PostMapping("/devices")
    suspend fun registerDevice(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @Valid @RequestBody request: RegisterDeviceRequest,
    ): DeviceSnapshot = withContext(Dispatchers.IO) {
        service.registerDevice(RecipientType.WORKER, workerId, request.pushToken)
    }

    @DeleteMapping("/devices/{deviceId}")
    suspend fun unregisterDevice(
        @PathVariable deviceId: UUID,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): DeviceSnapshot = withContext(Dispatchers.IO) {
        service.unregisterDevice(RecipientType.WORKER, workerId, deviceId)
    }
}
