package com.snabbit.prototype.platform

interface GoogleIdentityVerifier {
    fun verify(idToken: String, allowedClientIds: Set<String>, expectedNonce: String? = null): GoogleIdentity
}
