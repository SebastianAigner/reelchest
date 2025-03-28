import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sebi.api.dtos.DuplicatesDTO
import io.sebi.config.AppConfig
import io.sebi.module
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopDuplicatesTest {

    private fun copyTestDatabase(testDir: String) {
        // Create all necessary directories
        File("$testDir/database").mkdirs()
        File("$testDir/mediaLibrary").mkdirs()
        File("$testDir/userConfig").mkdirs()

        // Copy the database file
        File("../database/db.sqlite").copyTo(File("$testDir/database/db.sqlite"))
    }

    private fun withTestServer(block: suspend (ApplicationEngine, HttpClient, Int) -> Unit) {
        withTestPaths {
            runBlocking {
                val server = embeddedServer(Netty, port = 0, host = "localhost") {
                    module()
                }.start(wait = false)

                val client = HttpClient(CIO)
                try {
                    // Give the server a moment to be ready
                    delay(500)
                    val port = server.engine.resolvedConnectors().first().port
                    block(server.engine, client, port)
                } finally {
                    client.close()
                    server.stop(1000, 1000)
                }
            }
        }
    }

    private fun withTestPaths(block: () -> Unit) {
        val originalWorkingDir = System.getProperty("user.dir")
        try {
            // Set working directory to parent (reelchest) directory
            System.setProperty("user.dir", File("..").absolutePath)

            // Create test directory with unique name
            val testDirName = "test_${System.currentTimeMillis()}"
            File(testDirName).mkdir()

            // Copy test database
            copyTestDatabase(testDirName)

            AppConfig.withPaths(
                userConfig = "$testDirName/userConfig",
                mediaLibrary = "$testDirName/mediaLibrary",
                database = "$testDirName/database/db.sqlite"
            ) {
                try {
                    block()
                } finally {
                    File(testDirName).deleteRecursively()
                }
            }
        } finally {
            System.setProperty("user.dir", originalWorkingDir)
        }
    }

    @Test
    fun testTopDuplicates() {
        withTestServer { _, client, port ->
            // Step 1: Get a media library entry ID
            println("[DEBUG_LOG] Getting media library entries")
            val mediaLibraryResponse = client.get("http://localhost:$port/api/mediaLibrary")
            assertEquals(HttpStatusCode.OK, mediaLibraryResponse.status)
            val mediaLibraryText = mediaLibraryResponse.bodyAsText()

            // Parse the JSON response to get the first entry's ID
            val mediaLibraryJson = Json.parseToJsonElement(mediaLibraryText).jsonArray
            assertTrue(mediaLibraryJson.isNotEmpty(), "Media library should not be empty")

            val firstEntryId = mediaLibraryJson[0].jsonObject["id"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("First entry should have an ID")

            println("[DEBUG_LOG] First entry ID: $firstEntryId")

            // Step 2: Create multiple duplicates with different distances
            println("[DEBUG_LOG] Creating duplicates with different distances")
            val duplicates = listOf(
                DuplicatesDTO(src_id = firstEntryId, dup_id = "test-duplicate-1", distance = 10),
                DuplicatesDTO(src_id = firstEntryId, dup_id = "test-duplicate-2", distance = 5),
                DuplicatesDTO(src_id = firstEntryId, dup_id = "test-duplicate-3", distance = 20)
            )

            for (duplicate in duplicates) {
                val createResponse =
                    client.post("http://localhost:$port/api/mediaLibrary/${duplicate.src_id}/storedDuplicate") {
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(DuplicatesDTO.serializer(), duplicate))
                    }
                assertEquals(HttpStatusCode.OK, createResponse.status)
            }

            // Step 3: Get the top duplicates
            println("[DEBUG_LOG] Getting top duplicates")
            val topDuplicatesResponse = client.get("http://localhost:$port/api/mediaLibrary/duplicates")
            assertEquals(HttpStatusCode.OK, topDuplicatesResponse.status)

            val topDuplicatesText = topDuplicatesResponse.bodyAsText()
            println("[DEBUG_LOG] Top duplicates response: $topDuplicatesText")

            // Parse the JSON response
            val topDuplicatesJson = Json.parseToJsonElement(topDuplicatesText).jsonArray
            assertTrue(topDuplicatesJson.isNotEmpty(), "Top duplicates should not be empty")

            // Step 4: Verify the duplicates are returned in the correct order (by distance)
            // The duplicate with distance 5 should be first
            val firstDuplicate = Json.decodeFromString(DuplicatesDTO.serializer(), topDuplicatesJson[0].toString())
            assertEquals(
                "test-duplicate-2",
                firstDuplicate.dup_id,
                "First duplicate should be the one with lowest distance"
            )
            assertEquals(5, firstDuplicate.distance, "First duplicate should have distance 5")
        }
    }
}