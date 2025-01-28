import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sebi.module
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerIntegrationTest {

    private fun setupWorkingDirectory(): String {
        // Store the original working directory
        val originalWorkingDir = System.getProperty("user.dir")
        // Set working directory to parent (reelchest) directory
        System.setProperty("user.dir", File("..").absolutePath)
        return originalWorkingDir
    }

    @Test
    fun testStartup() = runBlocking {
        val originalWorkingDir = setupWorkingDirectory()
        try {
            val server = embeddedServer(Netty, port = 0, host = "localhost") {
                module()
            }.start(wait = false)

            // Give the server a moment to start
            delay(500)
            println("[DEBUG_LOG] Server started successfully")

            server.stop(1000, 1000)
        } finally {
            // Restore the original working directory
            System.setProperty("user.dir", originalWorkingDir)
        }
    }

    @Test
    fun testHelloWorld() = runBlocking {
        val originalWorkingDir = setupWorkingDirectory()

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
            // Restore the original working directory
            System.setProperty("user.dir", originalWorkingDir)
        }
    }
}