package io.sebi.api.handlers

import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.MediaLibrary
import io.sebi.ui.ProcessUploadedFilePartResult
import io.sebi.ui.copyToNIO
import io.sebi.ui.readAllPartsAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.slf4j.LoggerFactory
import java.io.File

suspend fun RoutingContext.uploadHandler(mediaLibrary: MediaLibrary) {
    val logger = LoggerFactory.getLogger("UploadHandler")
    logger.info("Starting upload handler.")
    logger.info("Receiving multipart data...")
    val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
    logger.info("Done.")
    var lastUploadedId: String? = null

    multipart
        .readAllPartsAsFlow()
        .filterIsInstance<PartData.FileItem>()
        .collect { fileItem ->
            val uploadResult = runCatching {
                logger.info("Got FileItem ${fileItem.originalFileName}.")
                logger.info(fileItem.headers.entries().joinToString(", ") { it.key + " " + it.value })
                val targetFile = File.createTempFile(
                    "vid",
                    ".mp4",
                    File("downloads").apply { mkdir(); }
                ).apply { deleteOnExit() }
                logger.info("Starting copy.")

                fileItem.streamProvider().let { stream ->
                    val targetPath = targetFile.toPath()
                    stream.copyToNIO(targetPath)
                }
                val result = ProcessUploadedFilePartResult(
                    targetFile!!,
                    fileItem.originalFileName!!
                )
                logger.info("Disposing part.")
                fileItem.dispose()
                result
            }

            if (uploadResult.isFailure) {
                call.respondText(
                    "an error has happened: ${uploadResult.exceptionOrNull()?.message}" +
                            "\n${uploadResult.exceptionOrNull()?.stackTraceToString()}"
                )
                error("Upload failed with ${uploadResult.exceptionOrNull()}")
            }

            if (uploadResult.isSuccess) {
                val (targetFile, name) = uploadResult.getOrNull()!!
                lastUploadedId = mediaLibrary.addUpload(
                    targetFile,
                    name = name
                )
            }
        }

    if (lastUploadedId != null) {
        call.respond(mapOf("id" to lastUploadedId))
    } else {
        call.respond(mapOf("error" to "No file was uploaded"))
    }

}