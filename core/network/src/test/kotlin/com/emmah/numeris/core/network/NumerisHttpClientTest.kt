package com.emmah.numeris.core.network

import com.emmah.numeris.core.common.NumerisFailure
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class NumerisHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `returns successful body and records exact request`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))

            val response =
                NumerisHttpClient().execute(
                    "Test provider",
                    Request
                        .Builder()
                        .url(server.url("/report?period=7d"))
                        .header("Authorization", "Bearer hidden")
                        .build(),
                )

            assertEquals("{\"ok\":true}", response.body)
            val request = server.takeRequest()
            assertEquals("/report?period=7d", request.path)
            assertEquals("Bearer hidden", request.getHeader("Authorization"))
        }

    @Test
    fun `maps rate limit without exposing body or query secret`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "60")
                .setBody("token=should-never-escape"),
        )

        val failure =
            assertThrows(NumerisFailure.RateLimit::class.java) {
                runBlocking {
                    NumerisHttpClient().execute(
                        "Test provider",
                        Request.Builder().url(server.url("/report?api_key=also-hidden")).build(),
                    )
                }
            }

        assertFalse(failure.message.orEmpty().contains("should-never-escape"))
        assertFalse(failure.message.orEmpty().contains("also-hidden"))
    }
}
