package com.snabbit.prototype.mobile.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianMobileNumberTest {
    private val nationalNumber = "9" + "0".repeat(8) + "1"

    @Test
    fun `normalizes supported Indian phone formats`() {
        assertEquals(nationalNumber, IndianMobileNumber.digits(nationalNumber))
        assertEquals(nationalNumber, IndianMobileNumber.digits("+91 $nationalNumber"))
        assertEquals(nationalNumber, IndianMobileNumber.digits("0$nationalNumber"))
    }

    @Test
    fun `validates and formats an Indian mobile number`() {
        assertTrue(IndianMobileNumber.isValid(nationalNumber))
        assertFalse(IndianMobileNumber.isValid("5" + "0".repeat(9)))
        assertEquals("+91$nationalNumber", IndianMobileNumber.e164(nationalNumber))
        assertEquals("+91 ••••••${nationalNumber.takeLast(4)}", IndianMobileNumber.masked("+91$nationalNumber"))
    }
}
