package com.snabbit.prototype.support.dto.response

import com.snabbit.prototype.support.domain.enums.SupportTicketStatus

import com.snabbit.prototype.support.domain.enums.SupportSenderType

import com.snabbit.prototype.support.domain.SupportMessage
import com.snabbit.prototype.support.domain.SupportTicket
import com.snabbit.prototype.support.repository.SupportMessageRepository
import com.snabbit.prototype.support.repository.SupportTicketRepository
import com.snabbit.prototype.support.security.InternalServiceClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class BookingOwnershipContext(val bookingId: UUID, val customerId: UUID)
