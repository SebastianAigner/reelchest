package io.sebi.api.handlers

import io.ktor.client.content.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.MediaLibrary
import io.sebi.library.file
import io.sebi.library.id

/**
 * Handler for serving a file by filename.
 */
suspend fun RoutingContext.serveFileByFilenameHandler(mediaLibrary: MediaLibrary) {
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

/**
 * Handler for serving a video by ID.
 */
suspend fun RoutingContext.serveVideoByIdHandler(mediaLibrary: MediaLibrary) {
    val rawId = call.parameters["id"]!!
    val shouldDownloadFile = rawId.endsWith(".mp4")
    val id = rawId.removeSuffix(".mp4")
    val type = ContentType.fromFileExtension("mp4").first()

    val entry = mediaLibrary.getEntries().first { it.id == id }
    if (shouldDownloadFile) {
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