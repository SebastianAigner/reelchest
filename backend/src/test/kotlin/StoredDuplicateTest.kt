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

class StoredDuplicateTest {

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
    fun testStoredDuplicate() {
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

            // Step 2: Create a duplicate for the entry
            println("[DEBUG_LOG] Creating a duplicate for entry $firstEntryId")
            val duplicateDto = DuplicatesDTO(
                src_id = firstEntryId,
                dup_id = "test-duplicate-id",
                distance = 42
            )

            val createResponse = client.post("http://localhost:$port/api/mediaLibrary/$firstEntryId/storedDuplicate") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(DuplicatesDTO.serializer(), duplicateDto))
            }
            assertEquals(HttpStatusCode.OK, createResponse.status)

            // Step 3: Retrieve the duplicate
            println("[DEBUG_LOG] Retrieving the duplicate for entry $firstEntryId")
            val getResponse = client.get("http://localhost:$port/api/mediaLibrary/$firstEntryId/storedDuplicate")
            assertEquals(HttpStatusCode.OK, getResponse.status)

            val getDuplicateText = getResponse.bodyAsText()
            println("[DEBUG_LOG] Retrieved duplicate: $getDuplicateText")

            // Parse the JSON response
            val retrievedDuplicate = Json.decodeFromString(DuplicatesDTO.serializer(), getDuplicateText)

            // Step 4: Verify the retrieved duplicate matches the one we created
            assertEquals(firstEntryId, retrievedDuplicate.src_id, "Source ID should match")
            assertEquals("test-duplicate-id", retrievedDuplicate.dup_id, "Duplicate ID should match")
            assertEquals(42, retrievedDuplicate.distance, "Distance should match")
        }
    }
}