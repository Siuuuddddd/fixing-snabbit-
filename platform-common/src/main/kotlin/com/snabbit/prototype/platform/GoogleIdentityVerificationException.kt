package com.snabbit.prototype.platform

class GoogleIdentityVerificationException(
    val failure: GoogleIdentityFailure,
) : RuntimeException("Google identity verification failed")
