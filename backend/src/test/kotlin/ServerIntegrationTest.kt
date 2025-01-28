import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sebi.config.AppConfig
import io.sebi.module
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServerIntegrationTest {

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
                    val port = server.resolvedConnectors().first().port
                    block(server, client, port)
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
    fun testStartup() {
        withTestServer { server, _, _ ->
            println("[DEBUG_LOG] Server started successfully")
        }
    }

    @Test
    fun testHelloWorld() {
        withTestServer { _, client, port ->
            println("[DEBUG_LOG] Server started on port: $port")
            println("[DEBUG_LOG] Attempting to connect to http://localhost:$port/api/status")
            val response = client.get("http://localhost:$port/api/status")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testMediaLibraryNotEmpty() {
        withTestServer { _, client, port ->
            println("[DEBUG_LOG] Checking media library entries")
            val response = client.get("http://localhost:$port/api/mediaLibrary")
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            println("[DEBUG_LOG] Media library response: $responseText")
            assertNotEquals("[]", responseText.trim(), "Media library should not be empty")
            assertTrue(responseText.contains("\"name\""), "Response should contain media entries with names")
        }
    }
}
