package io.sebi.urldecoder

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.sebi.downloader.CompletedDownloadTask
import io.sebi.downloader.DownloadTask
import io.sebi.network.NetworkManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import java.io.File
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

enum class DecryptedMediaType {
    SINGLE_FILE, M3U8, DASH, NONE
}

private val internalClient = HttpClient(Apache) {
    install(HttpTimeout) {
        socketTimeoutMillis = 20_000
        requestTimeoutMillis = 60_000
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
    val textFile = File("userConfig/decoders.json").apply { createNewFile() }

    val arr = Json.decodeFromString<JsonArray>(textFile.readText().ifEmpty { "[]" }) // todo ew
    val configurations = arr.map { Json.decodeFromJsonElement<UrlDecoderConfiguration>(it) }

    override suspend fun decodeUrl(url: String): DecryptResponse? {
        return configurations.firstNotNullOfOrNull config@{ decoder ->
            logger.info("Trying $decoder")
            val req = internalClient.post(decoder.endpoint + "/decrypt") {
                contentType(ContentType.Application.Json)
                setBody(DecryptRequest(url))
            }

            return if (req.status.isSuccess()) {
                val resultingUrl = req.body<DecryptResponse>()
                if (resultingUrl.urls.isNotEmpty()) resultingUrl else null
            } else null
        }
    }

    override suspend fun getMetadata(url: String): MetadataResponse? {
        return configurations.firstNotNullOfOrNull { decoder ->
                val metadata = internalClient.post(decoder.endpoint + "/metadata") {
                    contentType(ContentType.Application.Json)
                    setBody(DecryptRequest(url))
                }

                return if(metadata.status.isSuccess()) {
                    metadata.body<MetadataResponse>()
                } else {
                    // nothing found
                    logger.info("$decoder didn't find.")
                    null
                }
        }
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
        val mp4 = postProcessAudioAndVideo(vid, aud)
        return mp4.f
    }
}

fun postProcessAudioAndVideo(vid: DASHVideo, aud: DASHAudio): MP4File {
    val fileName = UUID.randomUUID().toString()
    ProcessBuilder("ffmpeg",
        "-y",
        "-i",
        vid.f.name,
        "-i",
        aud.f.name,
        "-c",
        "copy",
        "$fileName.mp4").directory(vid.f.parentFile).inheritIO().start().waitFor()
    return MP4File(File(vid.f.parent, "$fileName.mp4"))
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
    ProcessBuilder("ffmpeg", "-y", "-i", ts.f.name, "-c:v", "copy", "$fileName.mp4").directory(ts.f.parentFile)
        .inheritIO().start().waitFor()
    return MP4File(File(ts.f.parent, "$fileName.mp4"))
}

private val logger = LoggerFactory.getLogger("URL Decoder")
fun UrlDecoder.makeDownloadTask(originUrl: String, onComplete: suspend (CompletedDownloadTask) -> Unit): DownloadTask {
    val l = LazyDecoder(originUrl)
    return DownloadTask(originUrl, {
        l.decode(this)
    }, { files ->
        val postProcessor = when (l.mediaType) {
            DecryptedMediaType.SINGLE_FILE -> SingleFileOrErrorProcessor::postProcess
            DecryptedMediaType.M3U8 -> TransportStreamPostProcessor::postProcess
            DecryptedMediaType.DASH -> DashPostProcessor::postProcess
            else -> error("No postprocessor for ${l.mediaType}")
        }
        postProcessor(files)
    }, onComplete)
}

class LazyDecoder(val originUrl: String) {
    var mediaType: DecryptedMediaType? = null
    suspend fun decode(u: UrlDecoder): List<String> {
        val decoded = u.decodeUrl(originUrl) ?: error("Couldn't create DDLs for $originUrl")
        mediaType = decoded.type
        return decoded.urls
    }
}