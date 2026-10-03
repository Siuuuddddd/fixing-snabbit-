package com.snabbit.prototype.notification.controller

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.request.RegisterDeviceRequest
import com.snabbit.prototype.notification.dto.response.DeviceSnapshot
import com.snabbit.prototype.notification.dto.response.NotificationSnapshot
import com.snabbit.prototype.notification.service.NotificationService
import jakarta.validation.Valid
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val service: NotificationService) {
    @GetMapping
    suspend fun history(
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @RequestParam(defaultValue = "0") afterNotificationId: Long,
    ): List<NotificationSnapshot> = withContext(Dispatchers.IO) {
        service.history(RecipientType.CUSTOMER, customerId, afterNotificationId)
    }

    @PostMapping("/{notificationId}/read")
    suspend fun markRead(
        @PathVariable notificationId: Long,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): NotificationSnapshot = withContext(Dispatchers.IO) {
        service.markRead(RecipientType.CUSTOMER, customerId, notificationId)
    }

    @PostMapping("/devices")
    suspend fun registerDevice(
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: RegisterDeviceRequest,
    ): DeviceSnapshot = withContext(Dispatchers.IO) {
        service.registerDevice(RecipientType.CUSTOMER, customerId, request.pushToken)
    }

    @DeleteMapping("/devices/{deviceId}")
    suspend fun unregisterDevice(
        @PathVariable deviceId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): DeviceSnapshot = withContext(Dispatchers.IO) {
        service.unregisterDevice(RecipientType.CUSTOMER, customerId, deviceId)
    }
}
