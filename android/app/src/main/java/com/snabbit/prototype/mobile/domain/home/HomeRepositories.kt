package com.snabbit.prototype.mobile.domain.home

interface HomeFavoritesRepository {
    suspend fun preview(): FavoriteExpertsPreview
}

interface HabitPackPromoRepository {
    suspend fun promo(): HabitPackPromo
}
