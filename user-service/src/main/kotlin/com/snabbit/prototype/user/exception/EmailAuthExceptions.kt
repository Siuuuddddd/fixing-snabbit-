package com.snabbit.prototype.user.exception

class InvalidCredentials : RuntimeException("Email or password is invalid")
class EmailNotVerified : RuntimeException("Email verification is required")
class WeakPassword(val reasonCode: String) : RuntimeException("Password does not meet policy")
class InvalidOrExpiredCode : RuntimeException("The code is invalid or expired")
class TooManyCodeAttempts : RuntimeException("The code can no longer be used")
class TermsNotAccepted : RuntimeException("Terms must be accepted")
class PasswordNotSet : RuntimeException("Use forgot password to add a password")
class ValidationRejected(val fields: Map<String, String>) : RuntimeException("Request validation failed")
