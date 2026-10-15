package com.snabbit.prototype.worker.dto.response

import com.snabbit.prototype.worker.auth.WorkerPickerOption

data class WorkerPickerResponse(
    val code: String = "WORKER_SELECTION_REQUIRED",
    val selectionToken: String,
    val expiresIn: Long,
    val workers: List<WorkerPickerOption>,
    override val requiresWorkerSelection: Boolean = true,
) : WorkerSignInResponse
