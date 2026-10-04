package com.snabbit.prototype.mobile.data.tracking

import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.tracking.TrackingRepository
import com.snabbit.prototype.mobile.domain.tracking.WorkerLocation
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class WorkerLocationDto(
    val locationId: String,
    val bookingId: String,
    val visitIndex: Int,
    val workerId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: String,
    val receivedAt: String,
)

interface TrackingApi {
    @GET("api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location")
    suspend fun latest(
        @Path("bookingId") bookingId: String,
        @Path("visitIndex") visitIndex: Int,
    ): WorkerLocationDto
}

interface TrackingStreamDataSource {
    fun events(bookingId: String, visitIndex: Int, lastEventId: String?): Flow<WorkerLocationDto>
}

class OkHttpTrackingStreamDataSource(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String,
) : TrackingStreamDataSource {
    override fun events(bookingId: String, visitIndex: Int, lastEventId: String?): Flow<WorkerLocationDto> = callbackFlow {
        val url = baseUrl.trimEnd('/') + "/api/v1/bookings/$bookingId/visits/$visitIndex/worker-location/stream"
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").apply {
            lastEventId?.let { header("Last-Event-ID", it) }
        }.build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) close(DataFailure(DataFailureReason.OFFLINE))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!response.isSuccessful) throw response.code.toDataFailure()
                        val source = requireNotNull(response.body).source()
                        var eventName: String? = null
                        var data = StringBuilder()
                        while (!source.exhausted() && !call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> {
                                    if (eventName == "worker-location" && data.isNotEmpty()) {
                                        trySend(json.decodeFromString<WorkerLocationDto>(data.toString())).getOrThrow()
                                    }
                                    eventName = null
                                    data = StringBuilder()
                                }
                                line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                                line.startsWith("data:") -> {
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(line.substringAfter(':').trimStart())
                                }
                            }
                        }
                        if (eventName == "worker-location" && data.isNotEmpty() && !call.isCanceled()) {
                            trySend(json.decodeFromString<WorkerLocationDto>(data.toString())).getOrThrow()
                        }
                    }
                    close()
                } catch (failure: Throwable) {
                    if (!call.isCanceled()) close(failure)
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun Int.toDataFailure() = DataFailure(
        when (this) {
            401 -> DataFailureReason.UNAUTHORIZED
            403 -> DataFailureReason.FORBIDDEN
            404 -> DataFailureReason.NOT_FOUND
            409 -> DataFailureReason.CONFLICT
            422 -> DataFailureReason.VALIDATION
            in 500..599 -> DataFailureReason.SERVER
            else -> DataFailureReason.UNKNOWN
        },
    )
}

class DefaultTrackingRepository(
    private val api: TrackingApi,
    private val stream: TrackingStreamDataSource,
    private val json: Json,
) : TrackingRepository {
    override suspend fun latest(bookingId: String, visitIndex: Int): WorkerLocation = apiCall(json) {
        api.latest(bookingId, visitIndex).toDomain()
    }

    override fun stream(bookingId: String, visitIndex: Int, lastEventId: String?): Flow<WorkerLocation> = flow {
        stream.events(bookingId, visitIndex, lastEventId).collect { emit(it.toDomain()) }
    }

    private fun WorkerLocationDto.toDomain() = WorkerLocation(
        locationId, bookingId, visitIndex, workerId, latitude, longitude, accuracyMeters, capturedAt, receivedAt,
    )
}
