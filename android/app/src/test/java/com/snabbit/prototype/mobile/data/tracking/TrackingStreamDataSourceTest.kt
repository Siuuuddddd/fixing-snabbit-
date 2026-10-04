package com.snabbit.prototype.mobile.data.tracking

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingStreamDataSourceTest {
    @Test
    fun `sends last event id and parses worker location event`() = runTest {
        val lastEventHeader = AtomicReference<String>()
        val requestedPath = AtomicReference<String>()
        val body = """
            id: location-2
            event: worker-location
            data: {"locationId":"location-2","bookingId":"booking","visitIndex":0,"workerId":"worker","latitude":19.1,"longitude":72.9,"accuracyMeters":12.5,"capturedAt":"2026-10-04T10:00:00Z","receivedAt":"2026-10-04T10:00:01Z"}

        """.trimIndent()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            lastEventHeader.set(chain.request().header("Last-Event-ID"))
            requestedPath.set(chain.request().url.encodedPath)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }.build()
        val source = OkHttpTrackingStreamDataSource(client, Json, "https://example.test/")

        val event = source.events("booking", 0, "location-1").first()

        assertEquals("location-1", lastEventHeader.get())
        assertEquals("/api/v1/bookings/booking/visits/0/worker-location/stream", requestedPath.get())
        assertEquals("location-2", event.locationId)
        assertEquals(19.1, event.latitude, 0.0)
    }
}
