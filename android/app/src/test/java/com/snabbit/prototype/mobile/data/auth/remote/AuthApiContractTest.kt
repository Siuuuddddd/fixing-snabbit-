package com.snabbit.prototype.mobile.data.auth.remote

import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.POST

class AuthApiContractTest {
    @Test
    fun `customer and worker paths match backend controllers`() {
        assertPostPath("requestCustomerOtp", "api/v1/auth/otp/request")
        assertPostPath("verifyCustomerOtp", "api/v1/auth/otp/verify")
        assertPostPath("requestWorkerOtp", "api/v1/worker-auth/otp/request")
        assertPostPath("verifyWorkerOtp", "api/v1/worker-auth/otp/verify")
    }

    private fun assertPostPath(methodName: String, expected: String) {
        val method: Method = AuthApi::class.java.methods.single { it.name == methodName }
        val annotation = requireNotNull(method.getAnnotation(POST::class.java))
        assertEquals(expected, annotation.value)
    }
}
