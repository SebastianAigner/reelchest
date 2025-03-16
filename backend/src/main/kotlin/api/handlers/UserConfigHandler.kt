package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Handler for the user config read endpoint.
 */
suspend fun RoutingContext.userConfigReadHandler(fileName: String) {
    call.respond(File("userConfig/$fileName.txt").readText())
}

/**
 * Handler for the user config write endpoint.
 */
suspend fun RoutingContext.userConfigWriteHandler(fileName: String) {
    val newRules = call.receive<String>()
    if (newRules.isNotEmpty()) {
        File("userConfig/$fileName.txt").writeText(newRules)
    }
    call.respond(HttpStatusCode.OK)
}
