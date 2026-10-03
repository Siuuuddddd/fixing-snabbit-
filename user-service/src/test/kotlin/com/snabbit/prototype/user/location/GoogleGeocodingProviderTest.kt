package com.snabbit.prototype.user.location

import java.math.BigDecimal
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GoogleGeocodingProviderTest {
    @Test
    fun `maps forward and reverse Google geocoding responses`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = GoogleGeocodingProvider(
            builder,
            GeocodingProperties("google", "https://maps.googleapis.test", "api-key", 250.0),
        )
        server.expect(requestTo(startsWith("https://maps.googleapis.test/maps/api/geocode/json")))
            .andExpect(queryParam("address", "1%20Example%20Road,%20Mumbai%20400001,%20India"))
            .andExpect(queryParam("key", "api-key"))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))
        server.expect(requestTo(startsWith("https://maps.googleapis.test/maps/api/geocode/json")))
            .andExpect(queryParam("latlng", "19.0761,72.8778"))
            .andExpect(queryParam("key", "api-key"))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

        val forward = provider.geocode("1 Example Road", "Mumbai", "400001")
        val reverse = provider.reverseGeocode(BigDecimal("19.0761"), BigDecimal("72.8778"))

        assertEquals("Mumbai", forward?.city)
        assertEquals("400001", reverse?.pincode)
        server.verify()
    }

    private fun response() = """
        {
          "status":"OK",
          "results":[{
            "formatted_address":"1 Example Road, Mumbai, Maharashtra 400001, India",
            "geometry":{"location":{"lat":19.0760,"lng":72.8777}},
            "address_components":[
              {"long_name":"Mumbai","types":["locality"]},
              {"long_name":"400001","types":["postal_code"]}
            ]
          }]
        }
    """.trimIndent()
}
