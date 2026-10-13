package com.snabbit.prototype.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class IndianPhoneNumberTest {
    @ParameterizedTest
    @CsvSource(
        "+919876543210,+919876543210",
        "919876543210,+919876543210",
        "09876543210,+919876543210",
        "9876543210,+919876543210",
    )
    fun `normalizes supported Indian mobile formats`(input: String, expected: String) {
        assertEquals(expected, IndianPhoneNumber.normalize(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["987654321", "98765432100", "+91887654321", "5876543210", "98765abc10"])
    fun `rejects invalid Indian mobile numbers`(input: String) {
        assertThrows(IllegalArgumentException::class.java) { IndianPhoneNumber.normalize(input) }
    }
}
