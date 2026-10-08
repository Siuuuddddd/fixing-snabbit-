package com.snabbit.prototype.mobile.data.home.fake

import com.snabbit.prototype.mobile.domain.home.FavoriteExpertsPreview
import com.snabbit.prototype.mobile.domain.home.HomeFavoritesRepository

/** Temporary visual preview until the backend exposes a favourites read/list contract. */
class FakeHomeFavoritesRepository : HomeFavoritesRepository {
    override suspend fun preview() = FavoriteExpertsPreview(
        remainingExpertsToUnlock = 1,
        visibleAvatarCount = 3,
    )
}
