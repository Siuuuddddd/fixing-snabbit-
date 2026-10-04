package com.snabbit.prototype.mobile.data.rating

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.rating.RatingRepository
import com.snabbit.prototype.mobile.domain.rating.WorkerRating
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable data class RateWorkerDto(
    val bookingId: String,
    val visitIndex: Int,
    val stars: Int,
    val comment: String? = null,
)

@Serializable data class WorkerRatingDto(
    val ratingId: String,
    val bookingId: String,
    val visitIndex: Int,
    val workerId: String,
    val customerId: String,
    val stars: Int,
    val comment: String? = null,
    val workerAverageRating: Double,
)

interface RatingApi {
    @POST("api/v1/ratings/workers/{workerId}")
    suspend fun rate(@Path("workerId") workerId: String, @Body request: RateWorkerDto): WorkerRatingDto
}

class DefaultRatingRepository(
    private val api: RatingApi,
    private val json: Json,
) : RatingRepository {
    override suspend fun rateWorker(workerId: String, bookingId: String, visitIndex: Int, stars: Int, comment: String?) = apiCall(json) {
        api.rate(workerId, RateWorkerDto(bookingId, visitIndex, stars, comment?.trim()?.ifBlank { null })).let {
            WorkerRating(it.ratingId, it.bookingId, it.visitIndex, it.workerId, it.stars, it.comment, it.workerAverageRating.toString())
        }
    }
}
