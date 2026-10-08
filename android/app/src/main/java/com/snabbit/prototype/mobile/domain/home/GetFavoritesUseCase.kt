package com.snabbit.prototype.mobile.domain.home

class GetFavoritesUseCase(
    private val repository: HomeFavoritesRepository,
) {
    suspend operator fun invoke(): FavoriteExpertsPreview = repository.preview()
}
