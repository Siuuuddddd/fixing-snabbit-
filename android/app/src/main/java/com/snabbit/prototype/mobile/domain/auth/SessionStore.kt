package com.snabbit.prototype.mobile.domain.auth

import kotlinx.coroutines.flow.Flow

interface SessionStore {
    val session: Flow<AuthSession?>
    fun current(): AuthSession?
    fun save(session: AuthSession)
    fun clear()
}
