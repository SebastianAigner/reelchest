package io.sebi

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.serialization.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration

fun Application.installPlugins() {
    val logger = LoggerFactory.getLogger("Plugins")

    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            listOf("/api/queue", "/api/log", "/api/problematic").none {
                call.request.path().startsWith(it)
            }
        }
    }
    install(PartialContent)

    install(StatusPages) {
        exception<Throwable> { cause ->
            logger.error(cause.stackTraceToString())
            throw cause
        }
    }

    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()
    }

}