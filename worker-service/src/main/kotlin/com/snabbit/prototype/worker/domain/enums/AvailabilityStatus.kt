package com.snabbit.prototype.worker.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class AvailabilityStatus { AVAILABLE, UNAVAILABLE }
