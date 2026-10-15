package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.domain.auth.PendingWorkerSelection
import com.snabbit.prototype.mobile.domain.auth.PendingWorkerSelectionStore

class InMemoryPendingWorkerSelectionStore : PendingWorkerSelectionStore {
    @Volatile private var selection: PendingWorkerSelection? = null
    override fun current(): PendingWorkerSelection? = selection
    override fun save(selection: PendingWorkerSelection) { this.selection = selection }
    override fun clear() { selection = null }
}
