package com.snabbit.prototype.mobile.data.auth

import java.util.Base64
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwtSessionIdentityProviderTest {
    @Test
    fun `reads customer id from access token subject`() {
        val subject = "10000000-0000-0000-0000-000000000001"
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sub\":\"$subject\"}".encodeToByteArray())
        val provider = JwtSessionIdentityProvider(FakeIdentityTokenStore("header.$payload.signature"), Json)

        assertEquals(subject, provider.customerId())
    }

    @Test
    fun `malformed access token has no identity`() {
        val provider = JwtSessionIdentityProvider(FakeIdentityTokenStore("not-a-jwt"), Json)

        assertNull(provider.customerId())
    }
}

private class FakeIdentityTokenStore(private val token: String?) : TokenStore {
    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String) = Unit
    override fun clear() = Unit
}
