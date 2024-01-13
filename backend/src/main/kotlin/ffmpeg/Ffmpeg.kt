package io.sebi.ffmpeg

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import io.sebi.phash.DHash
import io.sebi.phash.JpegSplitter
import io.sebi.phash.writeULongs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.Blocking
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO

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

suspend fun generateThumbnails(videoFile: File) {
    val task = FfmpegTask(
        "ffmpeg",
        "-y",
        "-i",
        videoFile.name,
        "-q:v",
        "5",
        "-vf",
        "fps=1/60",
        "thumb%04d.jpg"
    )
    withTimeoutOrNull(120_000) {
        val (out, err) = task.execute(videoFile.parentFile)
        if (err.isNotEmpty()) logger.error(err.joinToString("\n"))
    }
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
