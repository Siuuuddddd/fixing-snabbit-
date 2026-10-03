package com.snabbit.prototype.worker.domain.enums

enum class WorkerTier(val surchargeMinor: Long) {
    STANDARD(0),
    VETERAN(3_000),
    EXPERT(5_000),
}
