package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.sebi.library.MediaLibraryEntry
import io.sebi.storage.JdbcSqliteMetadataStorage
import io.sebi.storage.MediaInformation
import io.sebi.storage.MediaProbeStatus
import io.sebi.utils.ReaderWriterLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
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

    @Test
    fun testMediaInformationSearchEndpointSupportsLimitAboveOneHundred() {
        withTestServer { _, client, port ->
            val metadataStorage = JdbcSqliteMetadataStorage(ReaderWriterLock())
            val prefix = "limit-over-100-${UUID.randomUUID()}"
            val createdIds =
                (1..125).map { index ->
                    val id = "$prefix-$index"
                    metadataStorage.storeMetadata(
                        id,
                        MediaLibraryEntry(
                            name = "$prefix-title-$index",
                            originUrl = "https://example.com/$id",
                            tags = emptySet(),
                            creationDate = System.currentTimeMillis() / 1000 + index,
                            uid = id,
                        )
                    )
                    metadataStorage.upsertMediaInformation(
                        MediaInformation(
                            libraryEntryId = id,
                            mimeType = "video/mp4",
                            codecType = "video",
                            codecName = "h264",
                            width = Int.MAX_VALUE - index,
                            height = 720,
                            bitRate = 1_000_000L + index,
                            frameRate = 30.0,
                            fileSize = 10_000_000L + index,
                            durationSeconds = 10.0 + index,
                            probeStatus = MediaProbeStatus.SUCCESS,
                            probeError = null,
                            lastProbeAt = System.currentTimeMillis(),
                        )
                    )
                    id
                }

            try {
                val searchResponse = client.get(
                    "http://localhost:$port/api/mediaLibrary/media-information/search?sort=width&order=desc&limit=125"
                )
                assertEquals(HttpStatusCode.OK, searchResponse.status)
                val searchJson = Json.parseToJsonElement(searchResponse.bodyAsText()).jsonArray
                assertEquals(125, searchJson.size)
                assertTrue(
                    searchJson.all { result ->
                        result.jsonObject["id"]?.jsonPrimitive?.content?.startsWith(prefix) == true
                    }
                )
            } finally {
                createdIds.forEach { id ->
                    metadataStorage.deleteMetadata(id)
                }
            }
        }
    }
}
