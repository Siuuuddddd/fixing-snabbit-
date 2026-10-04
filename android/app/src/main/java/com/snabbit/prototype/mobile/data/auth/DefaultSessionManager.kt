package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultSessionManager(private val tokenStore: TokenStore) : SessionManager {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val signedOut: Flow<Unit> = events
    override fun signOut() {
        tokenStore.clear()
        events.tryEmit(Unit)
    }
}
