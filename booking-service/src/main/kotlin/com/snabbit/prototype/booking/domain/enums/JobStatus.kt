package com.snabbit.prototype.booking.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

enum class JobStatus { ASSIGNED, ARRIVED, IN_PROGRESS, COMPLETED }
