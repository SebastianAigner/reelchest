package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SingleMediaInfoRequestTest : MediaLibraryTestBase() {
    @Test
    fun testSingleMediaInfoRequest() {
        withTestServer { _, client, port ->
            // First, get all media library entries
            val mediaLibraryResponse = client.get("http://localhost:$port/api/mediaLibrary")
            assertEquals(HttpStatusCode.OK, mediaLibraryResponse.status)
            val mediaLibraryJson = Json.parseToJsonElement(mediaLibraryResponse.bodyAsText()).jsonArray

            // Get the first entry's ID
            val firstId = (mediaLibraryJson.firstOrNull() as? JsonObject)?.get("id")?.jsonPrimitive?.content
            assertNotNull(firstId, "Media library should not be empty")

            println("[DEBUG_LOG] Testing media-info request for ID: $firstId")

            // Make a single request for media information
            val response = client.get("http://localhost:$port/api/mediaLibrary/$firstId/media-information")
            println("[DEBUG_LOG] Response status: ${response.status}")
            val responseBody = response.bodyAsText()
            println("[DEBUG_LOG] Response body: $responseBody")

            assertEquals(HttpStatusCode.OK, response.status)
            val bodyJson = Json.parseToJsonElement(responseBody).jsonObject
            assert(bodyJson.containsKey("durationSeconds")) { "Expected durationSeconds in media-info payload" }
        }
    }
}
