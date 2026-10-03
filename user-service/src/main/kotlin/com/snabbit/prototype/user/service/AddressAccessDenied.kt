package com.snabbit.prototype.user.service

import com.snabbit.prototype.user.domain.UserAddress
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.location.AddressLocationVerifier
import com.snabbit.prototype.user.repository.UserAddressRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AddressAccessDenied : RuntimeException("Authenticated customer does not own this address")
