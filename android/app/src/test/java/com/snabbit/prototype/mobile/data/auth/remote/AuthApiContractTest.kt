package com.snabbit.prototype.mobile.data.auth.remote

import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.POST

class AuthApiContractTest {
    @Test
    fun `Google session paths match backend controllers`() {
        assertPostPath("googleCustomer", "api/v1/auth/google")
        assertPostPath("googleWorker", "api/v1/worker-auth/google")
        assertPostPath("selectWorkerProfile", "api/v1/worker-auth/select-profile")
        assertPostPath("switchWorkerProfile", "api/v1/worker-auth/switch-profile")
        assertPostPath("refreshCustomer", "api/v1/auth/refresh")
        assertPostPath("refreshWorker", "api/v1/worker-auth/refresh")
        assertPostPath("logoutCustomer", "api/v1/auth/logout")
        assertPostPath("logoutAllCustomers", "api/v1/auth/logout-all")
        assertPostPath("logoutWorker", "api/v1/worker-auth/logout")
        assertPostPath("logoutAllWorkers", "api/v1/worker-auth/logout-all")
    }

    private fun assertPostPath(methodName: String, expected: String) {
        val method: Method = AuthApi::class.java.methods.single { it.name == methodName }
        val annotation = requireNotNull(method.getAnnotation(POST::class.java))
        assertEquals(expected, annotation.value)
    }
}
