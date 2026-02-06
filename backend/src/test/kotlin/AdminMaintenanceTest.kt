package io.sebi

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.sebi.config.AppConfig
import io.sebi.storage.JdbcSqliteMetadataStorage
import io.sebi.utils.ReaderWriterLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.*
import kotlin.test.*

class AdminMaintenanceTest : MediaLibraryTestBase() {
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
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals(1, json["orphansFound"]?.jsonPrimitive?.content?.toInt())
            assertEquals(1, json["orphansDeleted"]?.jsonPrimitive?.content?.toInt())
            assertEquals(0, json["failedDeletions"]?.jsonPrimitive?.content?.toInt())
            assertEquals(30L, json["bytesFreed"]?.jsonPrimitive?.content?.toLong())

            assertFalse(mediaFile.exists())
            assertFalse(thumbFile.exists())
            assertTrue(hashFile.exists())
        }
    }
}
