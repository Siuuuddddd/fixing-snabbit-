package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JwtSessionIdentityProvider(
    private val tokenStore: TokenStore,
    private val json: Json,
) : SessionIdentityProvider {
    override fun customerId(): String? = runCatching {
        val payload = requireNotNull(tokenStore.accessToken()).split('.').getOrNull(1)
            ?: return@runCatching null
        val decoded = Base64.getUrlDecoder().decode(payload.padBase64()).decodeToString()
        json.parseToJsonElement(decoded).jsonObject[SUBJECT]?.jsonPrimitive?.content
    }.getOrNull()

    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)

    private companion object {
        const val SUBJECT = "sub"
    }
}
