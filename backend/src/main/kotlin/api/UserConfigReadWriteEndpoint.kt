package io.sebi.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
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