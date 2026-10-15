package com.snabbit.prototype.mobile.domain.auth

interface PasswordCredentialSaver {
    suspend fun save(email: String, password: String)
}

interface PendingPasswordCredentialStore {
    fun save(email: String, password: String)
    fun consume(email: String): Pair<String, String>?
    fun clear()
}

interface AuthNoticeStore {
    fun publish(notice: AuthNotice)
    fun consume(): AuthNotice?
}

enum class AuthNotice { PASSWORD_UPDATED, SESSION_REVOKED }

