package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class WorkerCompletionCountSnapshot(val workerId: UUID, val completedJobs: Long)
