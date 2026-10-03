package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.dto.response.WorkerLocationResponse
import java.util.UUID

interface WorkerLocationClient {
    fun latest(workerId: UUID): WorkerLocationResponse?
}
