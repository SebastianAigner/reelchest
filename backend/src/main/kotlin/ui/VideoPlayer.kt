package io.sebi.ui

import io.ktor.application.*
import io.ktor.client.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.get
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