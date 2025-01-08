package io.sebi.subtitles

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.ffmpeg.FfmpegTask
import io.sebi.utils.copyToTempFile
import io.sebi.utils.createZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

fun Route.subtitleApi() {
    post("shiftsubtitles") {
        val multiPartData = call.receiveMultipart()
        val subtitleFiles: MutableList<PartData.FileItem> = mutableListOf()
        var offset: Long = 0
        multiPartData.forEachPart { part ->
            println(part.name)
            println(part.contentType)
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "subtitleFile" -> subtitleFiles += part
                    }
                }

                is PartData.FormItem -> {
                    if (part.name == "offset") {
                        offset = part.value.toLongOrNull() ?: 0
                    }
                }

                else -> {
                    // Ignore other part types
                }
            }
        }
        check(subtitleFiles.isNotEmpty()) { "No subtitle file provided." }
        val shiftedFiles = subtitleFiles.map { subtitleFileItem ->
            val tempSF = subtitleFileItem.copyToTempFile()
            subtitleFileItem.dispose()
            val shiftedSF = (tempSF.parent / (tempSF.nameWithoutExtension + "_shifted$offset.srt"))
            FfmpegTask(
                "-itsoffset",
                "$offset",
                "-i",
                tempSF.absolutePathString(),
                "-c",
                "copy",
                shiftedSF.absolutePathString()
            ).execute(tempSF.parent.toFile())
            shiftedSF
        }
        if (shiftedFiles.size == 1) {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${shiftedFiles[0].fileName}\"")
            call.respondFile(shiftedFiles[0].toFile())
            return@post
        }
        val zippedFiles = shiftedFiles.createZipFile()
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${zippedFiles.fileName}\"")
        call.respondFile(zippedFiles.toFile())
    }
    post("embedsubtitles") {
        val multiPartData = call.receiveMultipart()
        var videoFile: PartData.FileItem? = null
        var subtitleFile: PartData.FileItem? = null
        var offset: Long = 0

        multiPartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "file1" -> videoFile = part
                        "file2" -> subtitleFile = part
                    }
                }

                is PartData.FormItem -> {
                    if (part.name == "offset") {
                        offset = part.value.toLongOrNull() ?: 0
                    }
                }

                else -> {
                    // Ignore other part types
                }
            }
        }

        if (videoFile == null || subtitleFile == null) {
            call.respond(HttpStatusCode.BadRequest, "Both video and subtitle files must be provided.")
            LoggerFactory.getLogger("SubtitleApi").error("Missing files in the request.")
            return@post
        }

        val tempDir = Files.createTempDirectory("subtitle_api_temp")
        val videoFilePath = tempDir.resolve("video_file")
        val subtitleFilePath = tempDir.resolve("subtitle_file.srt")
        videoFile!!.streamProvider().use { input ->
            videoFilePath.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        subtitleFile!!.streamProvider().use { input ->
            subtitleFilePath.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        multiPartData.forEachPart { part -> part.dispose() }

        withContext(Dispatchers.IO) {
            val res = FfmpegTask(
                "-i",
                videoFilePath.toAbsolutePath().toString(),
                "-f",
                "srt",
                "-i",
                subtitleFilePath.toAbsolutePath().toString(),
                "-map", "0:0",
                "-map", "0:1",
                "-map", "1:0",
                "-c:v", "copy",
                "-c:a", "copy",
                "-c:s", "mov_text",
                "outfile.mkv"
            ).execute(tempDir.toFile())
            println(res.stdout)
            println(res.stderr.joinToString("\n"))
        }
        LoggerFactory.getLogger("SubtitleApi").info("Temporary files created at: ${tempDir.toAbsolutePath()}")
        LoggerFactory.getLogger("SubtitleApi").info("Received video, subtitles, and offset: $offset")
        call.respond(HttpStatusCode.OK, "Subtitle embedding process initiated.")
    }
}