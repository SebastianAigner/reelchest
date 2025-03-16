package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.logging.InMemoryAppender
import io.sebi.logging.getSerializableRepresentation

/**
 * Handler for getting the application logs.
 */
suspend fun RoutingContext.logHandler() {
    call.respond(InMemoryAppender.getSerializableRepresentation())
}
