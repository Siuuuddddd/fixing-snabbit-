package com.snabbit.prototype.platform

internal class StubGoogleIdentityVerifier(
    private val identity: GoogleIdentity,
) : GoogleIdentityVerifier {
    override fun verify(
        idToken: String,
        allowedClientIds: Set<String>,
        expectedNonce: String?,
    ): GoogleIdentity = identity
}
