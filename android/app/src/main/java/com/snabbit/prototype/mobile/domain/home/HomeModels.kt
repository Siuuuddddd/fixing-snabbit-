package com.snabbit.prototype.mobile.domain.home

import com.snabbit.prototype.mobile.domain.address.SavedAddress

enum class HomeServiceCategory {
    HOUSE_CLEANING,
    DUSTING_AND_WIPING,
    BATHROOM_CLEANING,
    WASH_FOLD_ARRANGE,
    CLEANING_DISHES,
    MORE,
}

data class HomeServiceTile(
    val category: HomeServiceCategory,
    val serviceId: String?,
)

data class FavoriteExpertsPreview(
    val remainingExpertsToUnlock: Int,
    val visibleAvatarCount: Int,
)

data class HabitPackPromo(
    val visitCount: Int,
    val originalPriceMinor: Long,
    val offerPriceMinor: Long,
)

typealias SelectedHomeAddress = SavedAddress
