package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultSessionManager(private val sessionStore: SessionStore) : SessionManager {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val signedOut: Flow<Unit> = events
    override fun signOut() {
        sessionStore.clear()
        events.tryEmit(Unit)
    }
}
