package com.snabbit.prototype.wallet.domain.enums

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class FinanceRefundActionStatus { REQUESTED, SUCCEEDED, FAILED }
