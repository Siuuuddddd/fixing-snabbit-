package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

suspend fun <T> apiCall(json: Json, block: suspend () -> T): T = try {
    block()
} catch (failure: DataFailure) {
    throw failure
} catch (failure: IOException) {
    throw DataFailure(DataFailureReason.OFFLINE)
} catch (failure: HttpException) {
    val detail = failure.response()?.errorBody()?.string()?.let { body ->
        runCatching { json.decodeFromString<ApiProblem>(body).detail }.getOrNull()
    }
    val reason = when (failure.code()) {
        401 -> DataFailureReason.UNAUTHORIZED
        403 -> DataFailureReason.FORBIDDEN
        404 -> DataFailureReason.NOT_FOUND
        409 -> DataFailureReason.CONFLICT
        422 -> DataFailureReason.VALIDATION
        in 500..599 -> DataFailureReason.SERVER
        else -> DataFailureReason.UNKNOWN
    }
    throw DataFailure(reason, detail)
} catch (failure: CancellationException) {
    throw failure
} catch (_: Exception) {
    throw DataFailure(DataFailureReason.UNKNOWN)
}
