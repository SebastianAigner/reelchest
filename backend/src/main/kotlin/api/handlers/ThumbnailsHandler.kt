package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.library.MediaLibrary
import io.sebi.library.file
import io.sebi.library.getThumbnails
import org.slf4j.LoggerFactory

/**
 * Handler for getting thumbnails.
 */
suspend fun RoutingContext.getThumbnailsHandler(mediaLibrary: MediaLibrary) {
    val id = call.parameters["id"]!!
    val entry = mediaLibrary.findById(id)!!
    val out = entry.file!!.parentFile.list()!!.filter { it.startsWith("thumb") }.sorted()
    call.respond(out)
}

/**
 * Handler for getting a random thumbnail.
 */
suspend fun RoutingContext.getRandomThumbHandler(mediaLibrary: MediaLibrary) {
    val id = call.parameters["id"]!!
    val entry =
        mediaLibrary
            .findById(id)
            ?.getThumbnails()
            ?.randomOrNull()
            ?: return call.respond(HttpStatusCode.NotFound)
    call.respondFile(entry)
}

/**
 * Handler for getting entries without thumbnails.
 */
suspend fun RoutingContext.getMissingThumbnailsHandler(mediaLibrary: MediaLibrary) {
    val entriesWithoutThumbnails = mediaLibrary.getEntries().filter { entry ->
        val thumbnails = entry.getThumbnails()
        thumbnails == null || thumbnails.isEmpty()
    }
    call.respond(entriesWithoutThumbnails)
}

/**
 * Handler for regenerating a thumbnail.
 */
suspend fun RoutingContext.regenerateThumbnailHandler(mediaLibrary: MediaLibrary) {
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing id parameter")
    val entry = mediaLibrary.findById(id) ?: return call.respond(HttpStatusCode.NotFound, "Entry not found")

    call.response.cacheControl(CacheControl.NoCache(null))
    call.response.header("Access-Control-Allow-Origin", "http://localhost:8080")
    call.response.header("Access-Control-Allow-Credentials", "true")
    call.response.header("Cache-Control", "no-cache")
    call.response.header("Connection", "keep-alive")
    try {
        entry.file?.let { videoFile ->
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val logger = object : org.slf4j.Logger by LoggerFactory.getLogger("ThumbnailDebug") {
                    override fun info(msg: String) {
                        write("data: [INFO] $msg\n\n")
                        flush()
                    }

                    override fun error(msg: String) {
                        write("data: [ERROR] $msg\n\n")
                        flush()
                    }
                }

                write("data: Starting thumbnail regeneration for entry: ${entry.name}\n\n")
                write("data: Video file: ${videoFile.absolutePath}\n\n")
                flush()

                generateThumbnails(
                    videoFile,
                    logger,
                    onStdoutLine = { line ->
                        write("data: [FFMPEG-OUT] $line\n\n")
                        flush()
                    },
                    onStderrLine = { line ->
                        write("data: [FFMPEG-ERR] $line\n\n")
                        flush()
                    }
                )

                write("data: Thumbnail generation completed\n\n")
                flush()
            }
        } ?: call.respond(HttpStatusCode.NotFound, "Video file not found")
    } catch (e: Exception) {
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            write("data: [ERROR] Exception: ${e.message}\n\n")
            flush()
        }
    }
}
