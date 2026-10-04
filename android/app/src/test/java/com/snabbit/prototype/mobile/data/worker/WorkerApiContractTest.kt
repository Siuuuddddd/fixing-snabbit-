package com.snabbit.prototype.mobile.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

class WorkerApiContractTest {
    @Test fun `worker endpoints match backend controllers`() {
        assertPath("offers", GET::class.java, "api/v1/worker/bookings/offers")
        assertPath("jobs", GET::class.java, "api/v1/worker/bookings/jobs")
        assertPath("accept", POST::class.java, "api/v1/worker/bookings/{bookingId}/offers/{offerId}/accept")
        assertPath("reject", POST::class.java, "api/v1/worker/bookings/{bookingId}/offers/{offerId}/reject")
        assertPath("arrive", POST::class.java, "api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/arrive")
        assertPath("start", POST::class.java, "api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/start")
        assertPath("complete", POST::class.java, "api/v1/worker/bookings/{bookingId}/visits/{visitIndex}/complete")
        assertPath("availability", GET::class.java, "api/v1/workers/me/availability")
        assertPath("updateAvailability", PUT::class.java, "api/v1/workers/me/availability/{serviceDate}")
        assertPath("publishLocation", POST::class.java, "api/v1/workers/me/location")
    }

    private fun <A : Annotation> assertPath(name: String, type: Class<A>, expected: String) {
        val annotation = requireNotNull(WorkerApi::class.java.methods.single { it.name == name }.getAnnotation(type))
        val value = type.getMethod("value").invoke(annotation) as String
        assertEquals(expected, value)
    }
}
