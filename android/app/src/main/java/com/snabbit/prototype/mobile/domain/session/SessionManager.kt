package com.snabbit.prototype.mobile.domain.session

import kotlinx.coroutines.flow.Flow

interface SessionManager {
    val signedOut: Flow<Unit>
    fun signOut()
}
