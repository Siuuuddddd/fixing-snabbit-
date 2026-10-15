package com.snabbit.prototype.user.exception

class SessionRevoked : RuntimeException("Session is no longer valid")

class RefreshInProgress : RuntimeException("Refresh is already in progress")

class AuthRateLimited(val retryAfterSeconds: Long = 1) : RuntimeException("Authentication request rate limit exceeded")
