package com.snabbit.prototype.user.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class StaffRole { SUPPORT_AGENT, OPERATIONS_MANAGER, ADMIN, FINANCE_OPERATOR }
