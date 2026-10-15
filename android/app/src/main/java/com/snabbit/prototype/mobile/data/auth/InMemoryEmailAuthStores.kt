package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.domain.auth.AuthNotice
import com.snabbit.prototype.mobile.domain.auth.AuthNoticeStore
import com.snabbit.prototype.mobile.domain.auth.PendingPasswordCredentialStore

class InMemoryPendingPasswordCredentialStore : PendingPasswordCredentialStore {
    private var value: Pair<String, String>? = null
    override fun save(email: String, password: String) { value = email to password }
    override fun consume(email: String): Pair<String, String>? = value?.takeIf { it.first == email }?.also { value = null }
    override fun clear() { value = null }
}

class InMemoryAuthNoticeStore : AuthNoticeStore {
    private var value: AuthNotice? = null
    override fun publish(notice: AuthNotice) { value = notice }
    override fun consume(): AuthNotice? = value.also { value = null }
}

