package com.snabbit.prototype.worker.exception

class WorkerOtpRateLimited : RuntimeException("Please wait before requesting another OTP")
