package io.sebi

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.websocket.*
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
        exception<Throwable> { call, cause ->
            logger.error(cause.stackTraceToString())
            throw cause
        }
    }

    install(WebSockets) {
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
        allowHeader(HttpHeaders.ContentType)
    }

}