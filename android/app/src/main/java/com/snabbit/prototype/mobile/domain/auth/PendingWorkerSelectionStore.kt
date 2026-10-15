package com.snabbit.prototype.mobile.domain.auth

data class PendingWorkerSelection(
    val selectionToken: String,
    val expiresAtEpochSeconds: Long,
    val workers: List<WorkerPickerOption>,
)

interface PendingWorkerSelectionStore {
    fun current(): PendingWorkerSelection?
    fun save(selection: PendingWorkerSelection)
    fun clear()
}
