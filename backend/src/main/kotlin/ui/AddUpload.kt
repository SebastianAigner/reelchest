package io.sebi.ui

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.MediaLibrary
import io.sebi.ui.shared.commonLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.html.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

val uploadPool = newFixedThreadPoolContext(2, "Uploader")

private val logger = LoggerFactory.getLogger("Upload")

fun Route.addUpload(mediaLibrary: MediaLibrary) {
    get("/upload") {
        call.respondHtml {
            commonLayout("upload file") {
                form {
                    id = "form"
                    attributes["hx-encoding"] = "multipart/form-data"
                    attributes["hx-post"] = "/ul"

                    input(type = InputType.file, name = "file") {
                        attributes["multiple"] = "true"
                    }
                    //input(type = InputType.submit)
                    button {
                        +"upload"
                    }
                    progress {
                        id = "progress"
                        value = "0"
                        max = "100"
                    }
                    script {
                        //language=JavaScript
                        +"""
                                    htmx.on('#form', 'htmx:xhr:progress', function(evt) {
                                      htmx.find('#progress').setAttribute('value', evt.detail.loaded/evt.detail.total * 100)
                                    });
                        """.trimIndent()
                    }
                }
//                form(action = "/ul", method = FormMethod.post, encType = FormEncType.multipartFormData) {
//                    input(type = InputType.file, name = "file") {
//                    }
//                    input(type = InputType.submit)
//                }
            }
        }
    }
    post("/ul") {
        logger.info("Starting upload handler.")
        logger.info("Receiving multipart data...")
        val multipart = call.receiveMultipart()
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
}

fun MultiPartData.readAllPartsAsFlow(): Flow<PartData> {
    return flow {
        while (true) {
            val part = readPart() ?: break
            emit(part)
        }
    }
}

data class ProcessUploadedFilePartResult(
    val file: File,
    val name: String,
)

suspend fun InputStream.copyToNIO(
    out: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val instr = this
    withContext(dispatcher) {
        Files.copy(
            instr,
            out,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}


// TODO: Is this already the most elegant version to copy? (https://ryanharrison.co.uk/2018/09/20/ktor-file-upload-download.html)
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
