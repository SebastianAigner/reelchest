package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertNotEquals

class RoutesSmokeTest : MediaLibraryTestBase() {
    @Test
    fun testRootRouteExists() {
        withTestServer { _, client, port ->
            val response = client.get("http://localhost:$port/")
            println("[DEBUG_LOG] Root route response status: ${response.status}")
            println("[DEBUG_LOG] Root route response body: ${response.bodyAsText()}")

            assertNotEquals(HttpStatusCode.NotFound, response.status, "Root route should not return 404")
        }
    }
}