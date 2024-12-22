package io.sebi.ffmpeg

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import io.sebi.phash.DHash
import io.sebi.phash.JpegSplitter
import io.sebi.phash.writeULongs
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.Blocking
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class FfmpegProcessResult(val stdout: List<String>, val stderr: List<String>)

class FfmpegTask(val parameters: List<String>) {
    constructor(vararg parameters: String?) : this(parameters.filterNotNull().toList())

    suspend fun execute(directory: File?): FfmpegProcessResult {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val res = process(
            "ffmpeg", *parameters.toTypedArray(),
            stdout = Redirect.Consume { flow ->
                flow.toList(stdout)
            },
            stderr = Redirect.Consume { flow ->
                flow.toList(stderr)
            }, directory = directory
        )
        return FfmpegProcessResult(stdout, stderr)
    }
}

val logger = LoggerFactory.getLogger("ffmpeg")

// https://sebi.io/posts/2024-12-21-faster-thumbnail-generation-with-ffmpeg-seeking/
suspend fun generateThumbnails(videoFile: File) {
    val dur = getVideoDuration(videoFile)
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
            add("thumb${streamId.toString().padStart(4, '0')}.jpg")
            streamId++
        }
    }

    val (out, err) = FfmpegTask(ffmpegParameters).execute(videoFile.parentFile)
    if (err.isNotEmpty()) logger.error(err.joinToString("\n"))
}


@Blocking
fun generateDHashes(videoFile: File) {
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
    JpegSplitter.split(output) {
        val hash = DHash.fromImage(ImageIO.read(it.inputStream()))
        hashColl += hash
    }

    proc.waitFor()

    println("Saving ${hashColl.count()} hashes for later.")
    File(videoFile.parent, "dhashes.bin").writeULongs(hashColl.map { it.raw })
}

val durationRegex = """(\d+\.?\d*)""".toRegex()
fun getVideoDuration(inputFile: File): Duration {
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
    return durationStr.toDouble().seconds
}