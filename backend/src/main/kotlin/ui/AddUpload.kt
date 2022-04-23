package io.sebi.ui

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.MediaLibrary
import io.sebi.ui.shared.commonLayout
import kotlinx.coroutines.*
import kotlinx.html.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

val uploadPool = newFixedThreadPoolContext(2, "Uploader")

private val logger = LoggerFactory.getLogger("Upload")

fun Route.addUpload(mediaLibrary: MediaLibrary) {
    get("/upload") {
        call.respondHtml {
            commonLayout("upload file") {
                form(action = "/ul", method = FormMethod.post, encType = FormEncType.multipartFormData) {
                    input(type = InputType.file, name = "file") {
                    }
                    input(type = InputType.submit)
                }
            }
        }
    }
    post("/ul") {
        logger.info("Starting upload handler.")
        val uploadResult = kotlin.runCatching {
            logger.info("Receiving multipart data...")
            val multipart = call.receiveMultipart()
            logger.info("Done.")
            var uploadedFile: File? = null
            var uploadedFileName: String? = null
            multipart.forEachPart {
                if (it is PartData.FileItem) {
                    logger.info("Got FileItem ${it.originalFileName}.")
                    logger.info(it.headers.entries().joinToString(", ") { it.key + " " + it.value })

                    val targetFile = File.createTempFile(
                        "vid",
                        ".mp4",
                        File("downloads").apply { mkdir(); }
                    ).apply { deleteOnExit() }
                    logger.info("Starting non-blocking copy.")
                    it.streamProvider().use { partstream ->
                        // TODO: Is this already the most elegant version to copy? (https://ryanharrison.co.uk/2018/09/20/ktor-file-upload-download.html)
                        targetFile.outputStream().buffered().use {
                            partstream.copyToSuspend(it, dispatcher = uploadPool)
                        }
                    }
                    uploadedFile = targetFile
                    uploadedFileName = it.originalFileName
                }
                logger.info("Disposing part.")
                it.dispose()
            }
            uploadedFile!! to uploadedFileName
        }
        if (uploadResult.isFailure) {
            call.respondText(
                "an error has happened: ${uploadResult.exceptionOrNull()?.message}" +
                        "\n${uploadResult.exceptionOrNull()?.stackTraceToString()}"
            )
            return@post
        }
        if (uploadResult.isSuccess) {
            val (targetFile, name) = uploadResult.getOrNull()!!
            mediaLibrary.addUpload(
                targetFile,
                name = name ?: "unknown upload"
            )
        }

        call.respondRedirect("/lib")
    }
}


suspend fun InputStream.copyToSuspend(
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    yieldSize: Int = 4 * 1024 * 1024,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Long {
    return withContext(dispatcher) {
        val buffer = ByteArray(bufferSize)
        var bytesCopied = 0L
        var bytesAfterYield = 0L
        while (true) {
            val bytes = read(buffer).takeIf { it >= 0 } ?: break
            out.write(buffer, 0, bytes)
            if (bytesAfterYield >= yieldSize) {
                logger.info("Wrote $bytes, yielding.")
                yield()
                bytesAfterYield %= yieldSize
            }
            bytesCopied += bytes
            bytesAfterYield += bytes
        }
        return@withContext bytesCopied
    }
}