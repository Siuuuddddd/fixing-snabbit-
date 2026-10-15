package com.snabbit.prototype.worker.exception

class WorkerSessionRevoked : RuntimeException("Session is no longer valid")

class WorkerRefreshInProgress : RuntimeException("Refresh is already in progress")

class SharedWorkerSessionRequired : RuntimeException("Profile switching requires a shared development session")

class WorkerAuthRateLimited : RuntimeException("Authentication request rate limit exceeded")
