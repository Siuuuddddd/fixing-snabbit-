package com.snabbit.prototype.platform

object IndianPhoneNumber {
    private val mobile = Regex("^[6-9][0-9]{9}$")

    fun normalize(raw: String): String {
        val value = raw.trim()
        val national = when {
            value.startsWith("+91") -> value.drop(3)
            value.length == 12 && value.startsWith("91") -> value.drop(2)
            value.length == 11 && value.startsWith("0") -> value.drop(1)
            else -> value
        }
        require(national.matches(mobile)) { "Enter a valid Indian mobile number" }
        return "+91$national"
    }
}
