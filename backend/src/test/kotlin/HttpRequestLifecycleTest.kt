import io.ktor.server.engine.*
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.Socket
import kotlin.test.Test

class HttpRequestLifecycleTest {
    @Test
    fun cancelsCallOnClientDisconnect() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val server = embeddedServer(Netty, port = 0, host = "localhost") {
            routing {
                install(HttpRequestLifecycle) {
                    cancelCallOnClose = true
                }
                get("/slow") {
                    try {
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        cancelled.complete(Unit)
                        throw e
                    }
                }
            }
        }.start(wait = false)

        try {
            val port = server.engine.resolvedConnectors().first().port
            val socket = Socket("localhost", port)
            socket.getOutputStream().apply {
                write("GET /slow HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            delay(100)
            socket.close()

            withTimeout(5_000) {
                cancelled.await()
            }
        } finally {
            server.stop(1000, 1000)
        }
    }
}
