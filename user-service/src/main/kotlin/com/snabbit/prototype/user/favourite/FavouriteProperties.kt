package com.snabbit.prototype.user.favourite

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("favourites")
data class FavouriteProperties(
    val maxCount: Int = 3,
    val requireCompletedVisit: Boolean = true,
) {
    init {
        require(maxCount in 1..3) { "favourites.max-count must be between 1 and 3" }
    }
}
