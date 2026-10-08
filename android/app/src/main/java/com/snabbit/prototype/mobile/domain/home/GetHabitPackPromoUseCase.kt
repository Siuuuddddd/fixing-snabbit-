package com.snabbit.prototype.mobile.domain.home

class GetHabitPackPromoUseCase(
    private val repository: HabitPackPromoRepository,
) {
    suspend operator fun invoke(): HabitPackPromo = repository.promo()
}
