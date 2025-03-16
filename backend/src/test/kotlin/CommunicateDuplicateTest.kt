import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.WithTestServerTestBase
import io.sebi.api.DuplicatesDTO
import io.sebi.helpertools.communicateDuplicateToRemote
import io.sebi.storage.Duplicates
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class CommunicateDuplicateTest : WithTestServerTestBase() {

    // This will store the duplicate received by the test server
    private var receivedDuplicate: DuplicatesDTO? = null

    @Test
    fun testCommunicateDuplicateToRemote() {
        // Reset the received duplicate before the test
        receivedDuplicate = null

        // Start a test server that will receive the duplicate
        withCustomTestServer { _, _, port ->
            // Create a duplicate to send
            val duplicate = Duplicates(
                src_id = "test-source-id",
                dup_id = "test-duplicate-id",
                distance = 42
            )

            // Call the function we're testing
            runBlocking {
                communicateDuplicateToRemote("http://localhost:$port", duplicate)

                // Give the server a moment to process the request
                delay(500)
            }

            // Verify that the server received the correct duplicate
            val expectedDuplicate = DuplicatesDTO(
                src_id = "test-source-id",
                dup_id = "test-duplicate-id",
                distance = 42
            )

            assertEquals(expectedDuplicate.src_id, receivedDuplicate?.src_id, "Source ID should match")
            assertEquals(expectedDuplicate.dup_id, receivedDuplicate?.dup_id, "Duplicate ID should match")
            assertEquals(expectedDuplicate.distance, receivedDuplicate?.distance, "Distance should match")
        }
    }

    /**
     * Custom test server that sets up a specific route for handling duplicate requests.
     * This is different from the standard withTestServer method which uses the module() function.
     */
    private fun withCustomTestServer(block: suspend (ApplicationEngine, HttpClient, Int) -> Unit) {
        withTestPaths {
            runBlocking {
                val server = embeddedServer(Netty, port = 0, host = "localhost") {
                    // Install ContentNegotiation to handle JSON serialization
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    }

                    routing {
                        post("/api/mediaLibrary/{id}/storedDuplicate") {
                            println("[DEBUG_LOG] Server received request to /api/mediaLibrary/{id}/storedDuplicate")
                            val id = call.parameters["id"]!!
                            println("[DEBUG_LOG] ID parameter: $id")

                            try {
                                val duplicate = call.receive<DuplicatesDTO>()
                                println("[DEBUG_LOG] Received duplicate: $duplicate")

                                this@CommunicateDuplicateTest.receivedDuplicate = duplicate
                                println("[DEBUG_LOG] Updated receivedDuplicate: ${this@CommunicateDuplicateTest.receivedDuplicate}")

                                call.respond(HttpStatusCode.OK)
                            } catch (e: Exception) {
                                println("[DEBUG_LOG] Error receiving duplicate: ${e.message}")
                                e.printStackTrace()
                                call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
                            }
                        }
                    }
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
}
