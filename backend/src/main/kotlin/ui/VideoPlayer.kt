package io.sebi.ui

import io.ktor.client.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.sebi.library.MediaLibrary
import io.sebi.library.file
import io.sebi.library.id

fun Route.videoPlayer(mediaLibrary: MediaLibrary) {
    get("/api/file/{filename}") {
        val filename = call.parameters["filename"]!!
        val entry = mediaLibrary.getEntries().first { it.name == filename }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, entry.name).toString()
        )
        val type = ContentType.fromFileExtension("mp4").first()

        call.respond(
            LocalFileContent(
                entry.file,
                contentType = type
            )
        )

    }
    get("/api/video/{id}") {
        val rawId = call.parameters["id"]!!
        val shouldDownloadFile = rawId.endsWith(".mp4")
        val id = rawId.removeSuffix(".mp4")
        val type = ContentType.fromFileExtension("mp4").first()

        val entry = mediaLibrary.getEntries().first { it.id == id }
        if(shouldDownloadFile) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, entry.name)
                    .toString()
            )
        }

        call.respond(
            LocalFileContent(
                entry.file!!,
                contentType = type
            )
        )
    }
}