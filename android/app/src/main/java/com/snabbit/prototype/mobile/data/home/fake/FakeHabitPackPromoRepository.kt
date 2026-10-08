package com.snabbit.prototype.mobile.data.home.fake

import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HabitPackPromoRepository

/** Temporary visual promo until the backend exposes a habit-pack catalogue/claim contract. */
class FakeHabitPackPromoRepository : HabitPackPromoRepository {
    override suspend fun promo() = HabitPackPromo(
        visitCount = 3,
        originalPriceMinor = 89_700,
        offerPriceMinor = 27_900,
    )
}
