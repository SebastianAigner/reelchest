package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaInformationSearchTest : MediaLibraryTestBase() {
    @Test
    fun testMediaInformationSearchEndpoint() {
        withTestServer { _, client, port ->
            val mediaLibraryResponse = client.get("http://localhost:$port/api/mediaLibrary")
            assertEquals(HttpStatusCode.OK, mediaLibraryResponse.status)
            val mediaLibraryJson = Json.parseToJsonElement(mediaLibraryResponse.bodyAsText()).jsonArray

            val firstId = mediaLibraryJson.firstOrNull()?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
            assertNotNull(firstId, "Media library should not be empty")

            val infoResponse = client.get("http://localhost:$port/api/mediaLibrary/$firstId/media-information")
            assertEquals(HttpStatusCode.OK, infoResponse.status)

            val searchResponse = client.get(
                "http://localhost:$port/api/mediaLibrary/media-information/search?sort=resolution&order=desc&limit=5"
            )
            assertEquals(HttpStatusCode.OK, searchResponse.status)
            val searchJson = Json.parseToJsonElement(searchResponse.bodyAsText()).jsonArray
            assertTrue(searchJson.isNotEmpty(), "Expected at least one media information row")
            val firstRow = searchJson.first().jsonObject
            assertNotNull(firstRow["id"])
        }
    }
}
