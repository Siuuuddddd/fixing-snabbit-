package com.snabbit.prototype.user.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

enum class UserStatus { ACTIVE, SUSPENDED, DELETED }
