package io.sebi.ffmpeg

import io.sebi.phash.DHash
import io.sebi.phash.JpegSplitter
import io.sebi.phash.writeULongs
import io.sebi.process.runExternalProcess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.Blocking
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class FfmpegProcessResult(val stdout: List<String>, val stderr: List<String>)

val globalFfmpegMutex = Mutex()

class FfmpegTask(val parameters: List<String>) {
    constructor(vararg parameters: String?) : this(parameters.filterNotNull().toList())

    suspend fun execute(
        directory: File?,
        customStdoutHandler: ((String) -> Unit)? = null,
        customStderrHandler: ((String) -> Unit)? = null,
    ): FfmpegProcessResult {
        logger.info("Executing ffmpeg command with parameters: ${parameters.joinToString(" ")}")
        if (directory != null) {
            logger.info("Working directory: ${directory.absolutePath}")
        }

        return globalFfmpegMutex.withLock {
            logger.info("Acquired ffmpeg mutex")
            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()
            try {
                val res = runExternalProcess(
                    "ffmpeg", *parameters.toTypedArray(),
                    onStdoutLine = {
                        stdout.add(it)
                        logger.info("[FFMPEG-OUT] $it")
                        customStdoutHandler?.invoke(it)
                    },
                    onStderrLine = {
                        stderr.add(it)
                        logger.error("[FFMPEG-ERR] $it")
                        customStderrHandler?.invoke(it)
                    },
                    directory = directory
                )
                logger.info("FFmpeg process completed")
                FfmpegProcessResult(stdout, stderr)
            } catch (e: Exception) {
                logger.error("FFmpeg process failed: ${e.message}")
                throw e
            } finally {
                logger.info("Releasing ffmpeg mutex")
            }
        }
    }
}

val logger = LoggerFactory.getLogger("ffmpeg")

// https://sebi.io/posts/2024-12-21-faster-thumbnail-generation-with-ffmpeg-seeking/
suspend fun generateThumbnails(
    videoFile: File,
    logger: org.slf4j.Logger = LoggerFactory.getLogger("ffmpeg"),
    onStdoutLine: (String) -> Unit? = {},
    onStderrLine: (String) -> Unit? = {},
) {
    logger.info("Starting thumbnail generation for ${videoFile.name}")
    logger.info("Video file path: ${videoFile.absolutePath}")
    logger.info("Parent directory: ${videoFile.parent}")

    if (!videoFile.exists()) {
        logger.error("Video file does not exist")
        throw IllegalArgumentException("Video file does not exist: ${videoFile.absolutePath}")
    }

    if (!videoFile.canRead()) {
        logger.error("Cannot read video file")
        throw IllegalArgumentException("Cannot read video file: ${videoFile.absolutePath}")
    }
    val dur = try {
        getVideoDuration(videoFile).also {
            logger.info("Video duration: ${it.inWholeSeconds} seconds")
        }
    } catch (e: Exception) {
        logger.error("Failed to get video duration: ${e.message}")
        throw e
    }

    val thumbnailCount = (dur.inWholeSeconds / 10) + 1
    logger.info("Planning to generate $thumbnailCount thumbnails")

    val ffmpegParameters = buildList<String> {
        var streamId = 0
        add("-y")
        for (timestamp in 0..dur.inWholeSeconds step 10) {
            add("-ss")
            add(timestamp.toString())
            add("-i")
            add(videoFile.absolutePath)
            add("-q:v")
            add("5")
            add("-frames:v")
            add("1")
            add("-map")
            add("$streamId:v:0")
            val thumbName = "thumb${streamId.toString().padStart(4, '0')}.jpg"
            add(thumbName)
            logger.info("Adding thumbnail $thumbName at timestamp ${timestamp}s")
            streamId++
        }
    }

    logger.info("Starting ffmpeg process in directory: ${videoFile.parentFile.absolutePath}")

    val (out, err) = try {
        FfmpegTask(ffmpegParameters).execute(
            directory = videoFile.parentFile,
            customStdoutHandler = { onStdoutLine(it) },
            customStderrHandler = { onStderrLine(it) }
        )
    } catch (e: Exception) {
        logger.error("Failed to execute ffmpeg: ${e.message}")
        throw e
    }

    if (out.isNotEmpty()) logger.info("FFmpeg output:\n${out.joinToString("\n")}")
    if (err.isNotEmpty()) logger.error("FFmpeg errors:\n${err.joinToString("\n")}")

    // Verify generated thumbnails
    val generatedThumbnails =
        videoFile.parentFile.listFiles { file -> file.name.startsWith("thumb") && file.name.endsWith(".jpg") }
    logger.info("Generated ${generatedThumbnails?.size ?: 0} thumbnails out of $thumbnailCount planned")
}


@Blocking
suspend fun generateDHashes(videoFile: File) {
    globalFfmpegMutex.withLock {
        val proc = ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i",
            videoFile.name,
            "-q:v",
            "5",
            "-vf",
            "fps=1",
            "-f",
            "image2pipe",
            "-"
        ).directory(videoFile.parentFile)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val output = proc.inputStream.buffered()

        val hashColl = mutableListOf<DHash>()
        JpegSplitter.split(
            output,
            onConcludeFile = {
                val hash = DHash.fromImage(ImageIO.read(it.inputStream()))
                hashColl += hash
            })

        proc.waitFor()

        println("Saving ${hashColl.count()} hashes for later.")
        File(
            videoFile.parent, "dhashes.bin"
        ).writeULongs(hashColl.map { it.raw })
    }
}

data class MediaTypeInfo(
    val codecType: String,
    val codecName: String,
)

suspend fun getMediaType(inputFile: File): MediaTypeInfo {
    return globalFfmpegMutex.withLock {
        val process = ProcessBuilder(
            "ffprobe",
            "-v", "quiet",
            "-print_format", "json",
            "-show_entries", "stream=codec_type,codec_name",
            inputFile.absolutePath
        ).start()

        val output = process.inputStream.bufferedReader().readText()
        println("[DEBUG_LOG] ffprobe output: $output")

        val json = Json.parseToJsonElement(output)
        val streams = json.jsonObject["streams"]?.jsonArray
            ?: error("ffprobe on $inputFile didn't return any streams")

        val firstStream = streams.firstOrNull()?.jsonObject
            ?: error("ffprobe on $inputFile didn't return any streams")

        val codecType = firstStream["codec_type"]?.jsonPrimitive?.content
            ?: error("ffprobe on $inputFile didn't return a codec_type")
        val codecName = firstStream["codec_name"]?.jsonPrimitive?.content
            ?: error("ffprobe on $inputFile didn't return a codec_name")

        MediaTypeInfo(codecType, codecName)
    }
}

val durationRegex = """(\d+\.?\d*)""".toRegex()
suspend fun getVideoDuration(inputFile: File): Duration {
    return globalFfmpegMutex.withLock {
        val lines = ProcessBuilder(
            "ffprobe",
            "-v",
            "quiet",
            "-print_format",
            "json=c=1", // todo: unclear why this prints three lines rather than just one, i thought it was supposed to be compact.
            "-show_entries",
            "format=duration",
            inputFile.absolutePath
        ).start().inputStream.bufferedReader().readText()
        val (durationStr) = durationRegex.find(lines)?.destructured
            ?: error("ffprobe on $inputFile didn't return a duration")
        durationStr.toDouble().seconds
    }
}
