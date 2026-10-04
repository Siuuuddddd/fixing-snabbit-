package com.snabbit.prototype.mobile.domain.rating

data class WorkerRating(
    val ratingId: String,
    val bookingId: String,
    val visitIndex: Int,
    val workerId: String,
    val stars: Int,
    val comment: String?,
    val workerAverageRating: String,
)

interface RatingRepository {
    suspend fun rateWorker(
        workerId: String,
        bookingId: String,
        visitIndex: Int,
        stars: Int,
        comment: String?,
    ): WorkerRating
}
