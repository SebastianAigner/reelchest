package io.sebi.ui

import io.ktor.server.application.*
import io.ktor.client.content.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.sebi.library.MediaLibrary

fun Route.videoPlayer(mediaLibrary: MediaLibrary) {
    get("/api/video/{id}") {
        val id = call.parameters["id"]!!
        val type = ContentType.fromFileExtension("mp4").first()
        call.respond(
            LocalFileContent(
                mediaLibrary.entries.first { it.id == id }.file!!,
                contentType = type
            )
        )
    }
}