package com.snabbit.prototype.notification.service

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.snabbit.prototype.notification.domain.enums.DevicePlatform

import com.snabbit.prototype.notification.domain.enums.DeliveryStatus

import com.snabbit.prototype.notification.domain.enums.DeliveryChannel

import com.snabbit.prototype.notification.domain.DeviceRegistration
import com.snabbit.prototype.notification.domain.Notification
import com.snabbit.prototype.notification.domain.NotificationDelivery
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.repository.NotificationDeliveryRepository
import com.snabbit.prototype.notification.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NotificationNotFound(message: String) : RuntimeException(message)
