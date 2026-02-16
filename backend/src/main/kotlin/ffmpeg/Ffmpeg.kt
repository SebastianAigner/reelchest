package io.sebi.ffmpeg

import io.sebi.heavymutex.HeavyMutex
import io.sebi.phash.DHash
import io.sebi.phash.JpegSplitter
import io.sebi.phash.writeULongs
import io.sebi.process.StdoutHandler
import io.sebi.process.runExternalProcess
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

val globalFfmpegMutex = HeavyMutex("FfmpegMutex")

class FfmpegTask(val parameters: List<String>) {
    constructor(vararg parameters: String?) : this(parameters.filterNotNull().toList())

    suspend fun execute(
        directory: File?,
        mutexLabel: String,
        customStdoutHandler: ((String) -> Unit)? = null,
        customStderrHandler: ((String) -> Unit)? = null,
        processRef: java.util.concurrent.atomic.AtomicReference<Process?>? = null,
    ): FfmpegProcessResult {
        logger.info("Executing ffmpeg (nice -n 19) with parameters: ${parameters.joinToString(" ")}")
        if (directory != null) {
            logger.info("Working directory: ${directory.absolutePath}")
        }

        return globalFfmpegMutex.withLock(mutexLabel) {
            logger.info("Acquired ffmpeg mutex")
            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()
            try {
                runExternalProcess(
                    "nice", "-n", "19", "ffmpeg", *parameters.toTypedArray(),
                    stdoutHandler = StdoutHandler.Lines {
                        stdout.add(it)
                        logger.info("[FFMPEG-OUT] $it")
                        customStdoutHandler?.invoke(it)
                    },
                    onStderrLine = {
                        stderr.add(it)
                        logger.error("[FFMPEG-ERR] $it")
                        customStderrHandler?.invoke(it)
                    },
                    directory = directory,
                    processRef = processRef
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

private const val THUMBNAIL_INTERVAL_SECONDS = 10L
private const val THUMBNAIL_BATCH_SIZE = 5
private const val THUMBNAIL_THREAD_COUNT = 1
private const val THUMBNAIL_FILTER_THREAD_COUNT = 1
private const val THUMBNAIL_QUALITY = 5
private const val THUMBNAIL_MAX_WIDTH = 1920
private const val THUMBNAIL_MAX_HEIGHT = 1080
private val THUMBNAIL_SCALE_FILTER =
    "scale=w='min($THUMBNAIL_MAX_WIDTH,iw)':h='min($THUMBNAIL_MAX_HEIGHT,ih)'" +
            ":force_original_aspect_ratio=decrease:force_divisible_by=2"

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

    val timestamps = (0L..dur.inWholeSeconds step THUMBNAIL_INTERVAL_SECONDS).toList()
    val thumbnailCount = timestamps.size
    logger.info("Planning to generate $thumbnailCount thumbnails")

    val batches = timestamps.chunked(THUMBNAIL_BATCH_SIZE)
    var nextThumbIndex = 0

    logger.info("Starting ffmpeg process in directory: ${videoFile.parentFile.absolutePath}")
    batches.forEachIndexed { batchIndex, batch ->
        batch.forEachIndexed { localIndex, timestamp ->
            val thumbName = thumbnailName(nextThumbIndex + localIndex)
            logger.info("Adding thumbnail $thumbName at timestamp ${timestamp}s")
        }

        logger.info("Starting ffmpeg batch ${batchIndex + 1}/${batches.size} (${batch.size} thumbnails)")

        val ffmpegParameters = buildThumbnailFfmpegParameters(
            videoFile = videoFile,
            timestamps = batch,
            startIndex = nextThumbIndex,
            scaleFilter = THUMBNAIL_SCALE_FILTER,
            threadCount = THUMBNAIL_THREAD_COUNT,
            filterThreadCount = THUMBNAIL_FILTER_THREAD_COUNT
        )

        val (out, err) = try {
            FfmpegTask(ffmpegParameters).execute(
                directory = videoFile.parentFile,
                mutexLabel = "Thumbnail generation",
                customStdoutHandler = { onStdoutLine(it) },
                customStderrHandler = { onStderrLine(it) }
            )
        } catch (e: Exception) {
            logger.error("Failed to execute ffmpeg: ${e.message}")
            throw e
        }

        if (out.isNotEmpty()) logger.info("FFmpeg output:\n${out.joinToString("\n")}")
        if (err.isNotEmpty()) logger.error("FFmpeg errors:\n${err.joinToString("\n")}")

        nextThumbIndex += batch.size
    }

    // Verify generated thumbnails
    val generatedThumbnails =
        videoFile.parentFile.listFiles { file -> file.name.startsWith("thumb") && file.name.endsWith(".jpg") }
    logger.info("Generated ${generatedThumbnails?.size ?: 0} thumbnails out of $thumbnailCount planned")
}

internal fun buildThumbnailFfmpegParameters(
    videoFile: File,
    timestamps: List<Long>,
    startIndex: Int,
    scaleFilter: String,
    threadCount: Int,
    filterThreadCount: Int,
): List<String> {
    return buildList {
        add("-y")
        add("-threads")
        add(threadCount.toString())
        add("-filter_threads")
        add(filterThreadCount.toString())
        timestamps.forEachIndexed { index, timestamp ->
            add("-ss")
            add(timestamp.toString())
            add("-i")
            add(videoFile.absolutePath)
            add("-vf")
            add(scaleFilter)
            add("-q:v")
            add(THUMBNAIL_QUALITY.toString())
            add("-map")
            add("$index:v:0")
            add("-frames:v")
            add("1")
            add("-update")
            add("1")
            add(thumbnailName(startIndex + index))
        }
    }
}

private fun thumbnailName(index: Int): String {
    return "thumb${index.toString().padStart(4, '0')}.jpg"
}

@Blocking
suspend fun generateDHashes(videoFile: File) {
    globalFfmpegMutex.withLock("DHash generation") {
        val hashColl = mutableListOf<DHash>()
        runExternalProcess(
            "nice",
            "-n",
            "19",
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
            "-",
            stdoutHandler = StdoutHandler.Bytes { input ->
                val output = input.buffered()
                JpegSplitter.split(
                    output,
                    onConcludeFile = {
                        val hash = DHash.fromImage(ImageIO.read(it.inputStream()))
                        hashColl += hash
                    })
            },
            onStderrLine = { line ->
                logger.error("[FFMPEG-ERR] $line")
            },
            directory = videoFile.parentFile
        )

        println("Saving ${hashColl.count()} hashes for later.")
        File(
            videoFile.parent, "dhashes.bin"
        ).writeULongs(hashColl.map { it.raw })
    }
}

data class MediaTypeInfo(
    val codecType: String,
    val codecName: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitRate: Long? = null,
    val frameRate: Double? = null,
    val fileSize: Long? = null,
)

fun MediaTypeInfo.toMimeType(): String {
    return when (codecType.lowercase()) {
        "video" -> "video/${codecName.lowercase()}"
        "audio" -> "audio/${codecName.lowercase()}"
        else -> "application/octet-stream"
    }
}

data class MediaProbeInfo(
    val mediaTypeInfo: MediaTypeInfo,
    val durationSeconds: Double?,
)

private fun parseFrameRate(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    if (trimmed == "0/0") return null
    return if (trimmed.contains('/')) {
        val parts = trimmed.split('/')
        if (parts.size != 2) return null
        val numerator = parts[0].toDoubleOrNull() ?: return null
        val denominator = parts[1].toDoubleOrNull() ?: return null
        if (denominator == 0.0) null else numerator / denominator
    } else {
        trimmed.toDoubleOrNull()
    }
}

suspend fun getMediaProbeInfo(inputFile: File): MediaProbeInfo {
    return globalFfmpegMutex.withLock("Media info probe") {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val exitCode = runExternalProcess(
            "ffprobe",
            "-v", "quiet",
            "-print_format", "json",
            "-show_entries",
            "stream=codec_type,codec_name,width,height,bit_rate,avg_frame_rate,r_frame_rate:format=bit_rate,duration",
            inputFile.absolutePath,
            stdoutHandler = StdoutHandler.Lines { stdout.add(it) },
            onStderrLine = { stderr.add(it) },
            directory = null
        )
        if (exitCode != 0) {
            error("ffprobe on $inputFile failed with exit code $exitCode: ${stderr.joinToString("\n")}")
        }
        val output = stdout.joinToString("\n")

        val json = Json.parseToJsonElement(output)
        val streams = json.jsonObject["streams"]?.jsonArray
            ?: error("ffprobe on $inputFile didn't return any streams")

        val streamObject = streams
            .mapNotNull { it.jsonObject }
            .firstOrNull { it["codec_type"]?.jsonPrimitive?.content?.lowercase() == "video" }
            ?: streams.firstOrNull()?.jsonObject
            ?: error("ffprobe on $inputFile didn't return any streams")

        val codecType = streamObject["codec_type"]?.jsonPrimitive?.content
            ?: error("ffprobe on $inputFile didn't return a codec_type")
        val codecName = streamObject["codec_name"]?.jsonPrimitive?.content
            ?: error("ffprobe on $inputFile didn't return a codec_name")
        val width = streamObject["width"]?.jsonPrimitive?.content?.toIntOrNull()
        val height = streamObject["height"]?.jsonPrimitive?.content?.toIntOrNull()
        val streamBitRate = streamObject["bit_rate"]?.jsonPrimitive?.content?.toLongOrNull()
        val format = json.jsonObject["format"]?.jsonObject
        val formatBitRate = format
            ?.get("bit_rate")
            ?.jsonPrimitive
            ?.content
            ?.toLongOrNull()
        val bitRate = streamBitRate ?: formatBitRate
        val frameRate = parseFrameRate(streamObject["avg_frame_rate"]?.jsonPrimitive?.content)
            ?: parseFrameRate(streamObject["r_frame_rate"]?.jsonPrimitive?.content)
        val fileSize = inputFile.length().takeIf { it > 0 }
        val durationSeconds = format
            ?.get("duration")
            ?.jsonPrimitive
            ?.content
            ?.toDoubleOrNull()

        val mediaTypeInfo = MediaTypeInfo(codecType, codecName, width, height, bitRate, frameRate, fileSize)
        MediaProbeInfo(mediaTypeInfo, durationSeconds)
    }
}

suspend fun getMediaType(inputFile: File): MediaTypeInfo {
    return getMediaProbeInfo(inputFile).mediaTypeInfo
}

suspend fun getVideoDuration(inputFile: File): Duration {
    val durationSeconds = getMediaProbeInfo(inputFile).durationSeconds
        ?: error("ffprobe on $inputFile didn't return a duration")
    return durationSeconds.seconds
}
