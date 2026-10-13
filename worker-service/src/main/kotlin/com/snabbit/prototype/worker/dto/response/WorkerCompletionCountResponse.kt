package com.snabbit.prototype.worker.dto.response

import java.util.UUID

data class WorkerCompletionCountResponse(val workerId: UUID, val completedJobs: Long)
