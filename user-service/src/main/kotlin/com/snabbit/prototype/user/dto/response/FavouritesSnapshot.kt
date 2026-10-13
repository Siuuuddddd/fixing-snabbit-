package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class FavouritesSnapshot(
    val userId: UUID,
    val maxCount: Int,
    val favourites: List<FavouriteWorkerSnapshot>,
)
