package com.snabbit.prototype.user.plus

import java.time.Duration

data class PlusPlanProperties(
    val priceMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String? = null,
) {
    init {
        require(priceMinor >= 100 && priceMinor % 100 == 0L) {
            "Plus plan price must use positive whole-rupee increments"
        }
        require(durationDays in 1..365) { "Plus plan duration must be between 1 and 365 days" }
        require(passCount in 0..20) { "Plus plan pass count must be between 0 and 20" }
        require(
            passDiscountMinor >= 0 && passDiscountMinor % 100 == 0L &&
                (passCount == 0 || passDiscountMinor >= 100)
        ) {
            "Plus plan pass discount is invalid"
        }
    }
}
