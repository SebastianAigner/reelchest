import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
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

class ServerIntegrationTest {

    private fun withTestPaths(block: () -> Unit) {
        val originalWorkingDir = System.getProperty("user.dir")
        try {
            // Set working directory to parent (reelchest) directory
            System.setProperty("user.dir", File("..").absolutePath)

            // Create test directory with unique name
            val testDirName = "test_${System.currentTimeMillis()}"
            File(testDirName).mkdir()

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
        withTestPaths {
            runBlocking {
                val server = embeddedServer(Netty, port = 0, host = "localhost") {
                    module()
                }.start(wait = false)

                // Give the server a moment to start
                delay(500)
                println("[DEBUG_LOG] Server started successfully")

                server.stop(1000, 1000)
            }
        }
    }

    @Test
    fun testHelloWorld() {
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
                    println("[DEBUG_LOG] Server started on port: $port")
                    println("[DEBUG_LOG] Server environment: ${server.environment}")
                    println("[DEBUG_LOG] Attempting to connect to http://localhost:$port/api/status")
                    val response = client.get("http://localhost:$port/api/status")
                    assertEquals(HttpStatusCode.OK, response.status)
                } finally {
                    client.close()
                    server.stop(1000, 1000)
                }
            }
        }
    }
}
