package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.network.TokenRefresher
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionRefreshCoordinator(
    private val store: SessionStore,
    private val refresher: TokenRefresher,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            store.session.collectLatest { session ->
                if (session == null) return@collectLatest
                val refreshAtMillis = (session.expiresAtEpochSeconds - REFRESH_EARLY_SECONDS) * 1_000L
                delay((refreshAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                refresher.refreshAccessToken()
            }
        }
    }

    private companion object { const val REFRESH_EARLY_SECONDS = 60L }
}
