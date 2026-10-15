package com.snabbit.prototype.worker.auth

import java.util.UUID

interface WorkerSelectionGrantStore {
    fun issue(googleSubject: String, eligibleWorkerIds: Collection<UUID>): String
    fun consume(token: String, googleSubject: String): Set<UUID>?
}
