package com.snabbit.prototype.worker.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

enum class WorkerTier(val surchargeMinor: Long) {
    STANDARD(0),
    VETERAN(3_000),
    EXPERT(5_000),
}
