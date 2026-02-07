package io.sebi.urldecoder

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.sebi.config.AppConfig
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.downloader.DownloadTask
import io.sebi.ffmpeg.globalFfmpegMutex
import io.sebi.network.NetworkManager
import io.sebi.process.StdoutHandler
import io.sebi.process.runExternalProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.outputStream

interface UrlDecoder {
    suspend fun decodeUrl(url: String): DecryptResponse?
    suspend fun getMetadata(url: String): MetadataResponse?
}

@Serializable
data class MetadataResponse(val title: String, val tags: List<String>)

@Serializable
data class DecryptResponse(val urls: List<String>, val type: DecryptedMediaType)

@Serializable
data class ErrorResponse(val errorCode: String, val message: String)

class DecryptServiceException(
    val endpoint: String,
    val errorCode: String,
    message: String,
    val status: HttpStatusCode? = null,
) : RuntimeException(message)

enum class DecryptedMediaType {
    SINGLE_FILE, M3U8, DASH, NONE
}

private val internalClient = HttpClient(Apache5) {
    install(HttpTimeout) {
        socketTimeoutMillis = 120_000
        requestTimeoutMillis = 120_000
    }
    install(ContentNegotiation) {
        json()
    }
}

@Serializable
data class DecryptRequest(val url: String)

@Serializable
data class UrlDecoderConfiguration(val endpoint: String)

class UrlDecoderImpl(val networkManager: NetworkManager) : UrlDecoder {
    private val textFile = File(AppConfig.userConfigPath, "decoders.json").also { file ->
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("[]")
        }
    }

    private val configurations = run {
        val content = textFile.readText().ifBlank { "[]" }
        val arr = Json.decodeFromString<JsonArray>(content)
        arr.map { Json.decodeFromJsonElement<UrlDecoderConfiguration>(it) }
    }

    override suspend fun decodeUrl(url: String): DecryptResponse? {
        for (decoder in configurations) {
            logger.info("Trying $decoder")
            val req = internalClient.post(decoder.endpoint + "/decrypt") {
                contentType(ContentType.Application.Json)
                setBody(DecryptRequest(url))
            }

            if (req.status.isSuccess()) {
                val resultingUrl = req.body<DecryptResponse>()
                if (resultingUrl.urls.isNotEmpty()) return resultingUrl
                continue
            }

            val errorBody = runCatching { req.body<ErrorResponse>() }.getOrNull()
            if (errorBody?.errorCode == "NO_MATCH") {
                logger.info("$decoder didn't find.")
                continue
            }
            val message = errorBody?.message ?: "Decoder ${decoder.endpoint} failed with status ${req.status}"
            val errorCode = errorBody?.errorCode ?: "DECODER_ERROR"
            throw DecryptServiceException(decoder.endpoint, errorCode, message, req.status)
        }
        return null
    }

    override suspend fun getMetadata(url: String): MetadataResponse? {
        for (decoder in configurations) {
            val metadata = internalClient.post(decoder.endpoint + "/metadata") {
                contentType(ContentType.Application.Json)
                setBody(DecryptRequest(url))
            }

            if (metadata.status.isSuccess()) {
                return metadata.body<MetadataResponse>()
            }

            val errorBody = runCatching { metadata.body<ErrorResponse>() }.getOrNull()
            if (errorBody?.errorCode == "NO_MATCH") {
                logger.info("$decoder didn't find.")
                continue
            }
            val message = errorBody?.message ?: "Decoder ${decoder.endpoint} failed with status ${metadata.status}"
            val errorCode = errorBody?.errorCode ?: "DECODER_ERROR"
            throw DecryptServiceException(decoder.endpoint, errorCode, message, metadata.status)
        }
        return null
    }
}

abstract class PostProcessor {
    abstract fun postProcess(l: List<File>): File
}

object SingleFileOrErrorProcessor : PostProcessor() {
    override fun postProcess(l: List<File>): File {
        return l.single()
    }
}

sealed class TypedFile(val f: File)
class MP4File(f: File) : TypedFile(f)
class TSFile(f: File) : TypedFile(f)
class DASHVideo(f: File) : TypedFile(f)
class DASHAudio(f: File) : TypedFile(f)

object TransportStreamPostProcessor : PostProcessor() {
    override fun postProcess(l: List<File>): File {
        val ts = l.map { TSFile(it) }
        val unifiedTs = postProcessTransportStream(ts)
        val mp4 = postProcessTsToMp4(unifiedTs)
        return mp4.f
    }
}

object DashPostProcessor : PostProcessor() {
    override fun postProcess(l: List<File>): File {
        val vid = DASHVideo(l[0])
        val aud = DASHAudio(l[1])
        val mp4 = runBlocking(Dispatchers.IO) { postProcessAudioAndVideo(vid, aud) }
        return mp4.f
    }
}

private fun looksLikeM3u8(file: File): Boolean {
    if (!file.exists()) return false
    return runCatching {
        file.inputStream().buffered().use { stream ->
            val bytes = stream.readNBytes(256)
            val header = bytes.toString(Charsets.UTF_8)
            header.trimStart().startsWith("#EXTM3U")
        }
    }.getOrDefault(false)
}

private fun selectManifestUrl(files: List<File>, urls: List<String>): String? {
    val index = files.indexOfFirst { looksLikeM3u8(it) }
    if (index >= 0 && index < urls.size) return urls[index]
    return selectManifestUrlFromUrls(urls)
}

private fun selectManifestUrlFromUrls(urls: List<String>): String? {
    return urls.firstOrNull { it.contains(".m3u8", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains(".mpd", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains("m3u8", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains("mpd", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains("manifest", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains("playlist", ignoreCase = true) }
        ?: urls.firstOrNull { it.contains("master", ignoreCase = true) }
}

private data class FfmpegRunResult(val exitCode: Int, val stderr: List<String>)

private suspend fun runFfmpegCommand(
    mutexLabel: String,
    directory: File?,
    args: List<String>,
): FfmpegRunResult {
    val stderr = mutableListOf<String>()
    val exitCode = globalFfmpegMutex.withLock(mutexLabel) {
        runExternalProcess(
            "nice",
            "-n",
            "19",
            "ffmpeg",
            *args.toTypedArray(),
            stdoutHandler = StdoutHandler.Lines { logger.info("[FFMPEG-OUT] $it") },
            onStderrLine = {
                stderr.add(it)
                logger.error("[FFMPEG-ERR] $it")
            },
            directory = directory
        )
    }
    return FfmpegRunResult(exitCode, stderr)
}

private fun ensureFfmpegOutput(
    output: File,
    exitCode: Int,
    stderr: List<String>,
    description: String,
): File {
    if (exitCode != 0 || !output.exists() || output.length() == 0L) {
        val detail = stderr.lastOrNull()?.let { " Last ffmpeg stderr: $it" } ?: ""
        throw IOException("$description failed (exit=$exitCode). Output missing: ${output.absolutePath}.$detail")
    }
    return output
}

suspend fun postProcessAudioAndVideo(vid: DASHVideo, aud: DASHAudio): MP4File {
    val fileName = UUID.randomUUID().toString()
    val result = runFfmpegCommand(
        mutexLabel = "DASH remux",
        directory = vid.f.parentFile,
        args = listOf("-y", "-i", vid.f.name, "-i", aud.f.name, "-c", "copy", "$fileName.mp4")
    )
    return MP4File(
        ensureFfmpegOutput(
            File(vid.f.parent, "$fileName.mp4"),
            result.exitCode,
            result.stderr,
            "DASH remux"
        )
    )
}

// Concatenates TS files in preparation for remuxing
fun postProcessTransportStream(l: List<TSFile>): TSFile {
    val paths = l.map { it.f.toPath() }
    val output = Files.createTempFile(
        Path.of("downloads"),
        "vid",
        ".ts",
    )
    val outputStream = output.outputStream()
    for (path in paths) {
        Files.copy(path, outputStream)
    }
    return TSFile(output.toFile())
}

fun postProcessTsToMp4(ts: TSFile): MP4File {
    val fileName = UUID.randomUUID().toString()
    val result = runBlocking(Dispatchers.IO) {
        runFfmpegCommand(
            mutexLabel = "TS remux",
            directory = ts.f.parentFile,
            args = listOf("-y", "-i", ts.f.name, "-c:v", "copy", "$fileName.mp4")
        )
    }
    return MP4File(ensureFfmpegOutput(File(ts.f.parent, "$fileName.mp4"), result.exitCode, result.stderr, "TS remux"))
}

suspend fun postProcessM3u8ToMp4(playlistUrl: String): MP4File {
    val outputDir = File("downloads").apply { mkdirs() }
    val fileName = UUID.randomUUID().toString()
    val result = runFfmpegCommand(
        mutexLabel = "HLS remux",
        directory = outputDir,
        args = listOf("-y", "-i", playlistUrl, "-c", "copy", "$fileName.mp4")
    )
    return MP4File(ensureFfmpegOutput(File(outputDir, "$fileName.mp4"), result.exitCode, result.stderr, "HLS remux"))
}

suspend fun postProcessDashManifestToMp4(manifestUrl: String): MP4File {
    val outputDir = File("downloads").apply { mkdirs() }
    val fileName = UUID.randomUUID().toString()
    val result = runFfmpegCommand(
        mutexLabel = "DASH manifest remux",
        directory = outputDir,
        args = listOf("-y", "-i", manifestUrl, "-c", "copy", "$fileName.mp4")
    )
    return MP4File(
        ensureFfmpegOutput(
            File(outputDir, "$fileName.mp4"),
            result.exitCode,
            result.stderr,
            "DASH manifest remux"
        )
    )
}

private val logger = LoggerFactory.getLogger("URL Decoder")
fun UrlDecoder.makeDownloadTask(originUrl: String, onComplete: suspend (CompletedDownloadTask) -> Unit): DownloadTask {
    val l = LazyDecoder(originUrl)
    return DownloadTask(originUrl, {
        l.decode(this)
    }, { files ->
        when (l.mediaType) {
            DecryptedMediaType.SINGLE_FILE -> SingleFileOrErrorProcessor.postProcess(files)
            DecryptedMediaType.M3U8 -> {
                val playlistUrl = selectManifestUrl(files, l.lastDecodedUrls)
                if (playlistUrl != null) {
                    postProcessM3u8ToMp4(playlistUrl).f
                } else {
                    TransportStreamPostProcessor.postProcess(files)
                }
            }

            DecryptedMediaType.DASH -> DashPostProcessor.postProcess(files)
            else -> error("No postprocessor for ${l.mediaType}")
        }
    }, onComplete,
        directStreamUrlProvider = {
            when (l.mediaType) {
                DecryptedMediaType.M3U8, DecryptedMediaType.DASH -> selectManifestUrlFromUrls(l.lastDecodedUrls)
                else -> null
            }
        },
        onStreamProcess = { playlistUrl ->
            when (l.mediaType) {
                DecryptedMediaType.M3U8 -> postProcessM3u8ToMp4(playlistUrl).f
                DecryptedMediaType.DASH -> postProcessDashManifestToMp4(playlistUrl).f
                else -> error("No stream processor for ${l.mediaType}")
            }
        },
        streamProcessingEligible = { l.mediaType == DecryptedMediaType.M3U8 || l.mediaType == DecryptedMediaType.DASH }
    )
}

class LazyDecoder(val originUrl: String) {
    var mediaType: DecryptedMediaType? = null
    var lastDecodedUrls: List<String> = emptyList()
    suspend fun decode(u: UrlDecoder): List<String> {
        val decoded = u.decodeUrl(originUrl)
            ?: throw DecryptServiceException("local", "NO_MATCH", "No decoder could decrypt $originUrl")
        mediaType = decoded.type
        lastDecodedUrls = decoded.urls
        return lastDecodedUrls
    }
}
