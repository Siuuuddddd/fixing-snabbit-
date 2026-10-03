package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.dto.response.VisitSnapshot

import com.snabbit.prototype.booking.dto.response.BookingSnapshot

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import com.snabbit.prototype.booking.support.CustomerAccessDenied
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class WorkerTrackingUnavailable(message: String) : RuntimeException(message)
