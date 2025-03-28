import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendTest {
    @Test
    fun testHelloWorld() = runBlocking {

    val server = embeddedServer(Netty, port = 0, host = "localhost") {
            routing {
                get("/") {
                    call.respondText("Hello, World!")
                }
            }
        }.start(wait = false)

        val client = HttpClient(CIO)
        try {
            // Give the server a moment to be ready
            delay(500)
            val port = server.engine.resolvedConnectors().first().port
            println("[DEBUG_LOG] Server started on port: $port")
            println("[DEBUG_LOG] Server environment: ${server.environment}")
            println("[DEBUG_LOG] Attempting to connect to http://localhost:$port/")
            val response = client.get("http://localhost:$port/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello, World!", response.bodyAsText())
        } finally {
            client.close()
            server.stop(1000, 1000)
        }
    }
}
