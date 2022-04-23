package io.sebi.api

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.userConfigReadWriteEndpoint(fileName: String) {
    route(fileName) {
        get {
            call.respond(File("userConfig/$fileName.txt").readText())
        }
        post {
            val newRules = call.receive<String>()
            if (newRules.isNotEmpty()) {
                File("userConfig/$fileName.txt").writeText(newRules)
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}