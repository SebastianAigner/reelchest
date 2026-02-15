package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.sebi.config.AppConfig
import io.sebi.library.MediaLibraryEntry
import io.sebi.storage.JdbcSqliteMetadataStorage
import io.sebi.storage.MetadataResult
import io.sebi.utils.ReaderWriterLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.*
import kotlin.test.*

class AdminMaintenanceTest : MediaLibraryTestBase() {
    private suspend fun awaitOrphanScanCompletion(client: io.ktor.client.HttpClient, port: Int): JsonObject? {
        return withTimeoutOrNull(5000) {
            while (true) {
                val response = client.get("http://localhost:$port/api/mediaLibrary/debug/scan-orphans")
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val status = json["status"]?.jsonPrimitive?.content
                if (status != "running") {
                    return@withTimeoutOrNull json
                }
                delay(100)
            }
            error("Unreachable")
        }
    }

    @Test
    fun testDiskUsageEndpoint() {
        withTestServer { _, client, port ->
            val response = client.get("http://localhost:$port/api/mediaLibrary/disk-usage")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            val path = json["path"]?.jsonPrimitive?.content
            val total = json["totalBytes"]?.jsonPrimitive?.content?.toLongOrNull()
            val free = json["freeBytes"]?.jsonPrimitive?.content?.toLongOrNull()
            val usable = json["usableBytes"]?.jsonPrimitive?.content?.toLongOrNull()

            assertEquals(File(AppConfig.mediaLibraryPath).absolutePath, path)
            assertNotNull(total)
            assertNotNull(free)
            assertNotNull(usable)
            assertTrue(total >= 0L)
            assertTrue(free >= 0L)
            assertTrue(usable >= 0L)
        }
    }

    @Test
    fun testScanOrphansDeletesMediaAndThumbs() {
        withTestServer { _, client, port ->
            val metadataStorage = JdbcSqliteMetadataStorage(ReaderWriterLock())
            val id = "orphan-test-${UUID.randomUUID()}"
            metadataStorage.deleteMetadata(id)

            val entryDir = File(AppConfig.mediaLibraryPath, id).apply { mkdirs() }
            val mediaFile = File(entryDir, "$id.mp4").apply { writeBytes(ByteArray(10) { 1 }) }
            val thumbFile = File(entryDir, "thumb0001.jpg").apply { writeBytes(ByteArray(20) { 2 }) }
            val hashFile = File(entryDir, "dhashes.bin").apply { writeBytes(ByteArray(5) { 3 }) }

            val response = client.post("http://localhost:$port/api/mediaLibrary/debug/scan-orphans")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = awaitOrphanScanCompletion(client, port)
            assertNotNull(json, "Orphan scan did not complete in time.")

            assertEquals("completed", json["status"]?.jsonPrimitive?.content)
            assertEquals(1, json["orphansFound"]?.jsonPrimitive?.content?.toInt())
            assertEquals(1, json["orphansDeleted"]?.jsonPrimitive?.content?.toInt())
            assertEquals(0, json["failedDeletions"]?.jsonPrimitive?.content?.toInt())
            assertEquals(30L, json["bytesFreed"]?.jsonPrimitive?.content?.toLong())

            assertFalse(mediaFile.exists())
            assertFalse(thumbFile.exists())
            assertTrue(hashFile.exists())
        }
    }

    @Test
    fun testDeleteMarkedMediaRemovesEntries() {
        withTestServer { _, client, port ->
            val metadataStorage = JdbcSqliteMetadataStorage(ReaderWriterLock())
            val id = "delete-marked-${UUID.randomUUID()}"
            val entry = MediaLibraryEntry(
                name = "Delete me",
                originUrl = "https://example.com/$id",
                tags = emptySet(),
                creationDate = System.currentTimeMillis() / 1000,
                uid = id,
                markedForDeletion = true
            )
            metadataStorage.storeMetadata(id, entry)

            val entryDir = File(AppConfig.mediaLibraryPath, id).apply { mkdirs() }
            val mediaFile = File(entryDir, "$id.mp4").apply { writeBytes(ByteArray(12) { 7 }) }
            val thumbFile = File(entryDir, "thumb0001.jpg").apply { writeBytes(ByteArray(6) { 4 }) }

            val response = client.post("http://localhost:$port/api/mediaLibrary/debug/delete-marked")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            val totalMarked = json["totalMarked"]?.jsonPrimitive?.content?.toIntOrNull()
            val deleted = json["deleted"]?.jsonPrimitive?.content?.toIntOrNull()
            val failed = json["failed"]?.jsonPrimitive?.content?.toIntOrNull()

            assertNotNull(totalMarked)
            assertNotNull(deleted)
            assertNotNull(failed)
            assertTrue(totalMarked >= deleted)
            assertTrue(deleted >= 1)

            assertFalse(mediaFile.exists())
            assertFalse(thumbFile.exists())
            val result = metadataStorage.retrieveMetadata(id)
            assertTrue(result is MetadataResult.Tombstone || result is MetadataResult.None)
        }
    }
}
