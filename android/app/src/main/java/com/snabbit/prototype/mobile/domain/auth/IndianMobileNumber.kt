package com.snabbit.prototype.mobile.domain.auth

object IndianMobileNumber {
    private val nationalPattern = Regex("^[6-9][0-9]{9}$")

    fun digits(raw: String): String = raw.filter(Char::isDigit).let { value ->
        when {
            value.length > 10 && value.startsWith("91") -> value.drop(2)
            value.length > 10 && value.startsWith("0") -> value.drop(1)
            else -> value
        }.take(10)
    }

    fun isValid(digits: String): Boolean = digits.matches(nationalPattern)

    fun e164(digits: String): String {
        require(isValid(digits)) { "Enter a valid Indian mobile number" }
        return "+91$digits"
    }

    fun masked(e164: String): String = "+91 ••••••${e164.takeLast(4)}"
}
