package com.snabbit.prototype.user.exception

class OtpRateLimited : RuntimeException("Please wait before requesting another OTP")
