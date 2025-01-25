package io.sebi.api

import dz.jtsgen.annotations.TypeScript
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.analytics.analyticsApi
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.DownloadTaskDTO
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.library.withAutoTags
import io.sebi.logging.InMemoryAppender
import io.sebi.logging.getSerializableRepresentation
import io.sebi.network.NetworkManager
import io.sebi.storage.MetadataStorage
import io.sebi.subtitles.subtitleApi
import io.sebi.tagging.Tagger
import io.sebi.urldecoder.UrlDecoder
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Api")

val hashingInProgress = mutableListOf<MediaLibraryEntry>()


@OptIn(ExperimentalStdlibApi::class)
fun Route.api(
    urlDecoder: UrlDecoder,
    mediaLibrary: MediaLibrary,
    duplicateCalculator: DuplicateCalculator,
    downloadManager: DownloadManager,
    networkManager: NetworkManager,
    tagger: Tagger,
    metadataStorage: MetadataStorage,
) {

    route("api") {
        analyticsApi()
        get("config") {
            val env = application.environment
            val config = env.config

            call.respond(
                ApplicationConfig(
                    development = env.developmentMode,
                    port = config.property("ktor.deployment.port").getString().toInt(),
                    shutdownUrl = config.property("ktor.deployment.shutdown.url").getString(),
                    connectionGroupSize = config
                        .propertyOrNull("ktor.deployment.connectionGroupSize")
                        ?.getString()
                        ?.toInt() ?: Runtime.getRuntime().availableProcessors() * 2,
                    workerGroupSize = config.propertyOrNull("ktor.deployment.workerGroupSize")?.getString()?.toInt()
                        ?: Runtime.getRuntime().availableProcessors() * 2,
                    callGroupSize = config.propertyOrNull("ktor.deployment.callGroupSize")?.getString()?.toInt()
                        ?: Runtime.getRuntime().availableProcessors() * 2,
                    shutdownGracePeriod = config
                        .propertyOrNull("ktor.deployment.shutdownGracePeriod")
                        ?.getString()
                        ?.toLong() ?: 1000,
                    shutdownTimeout = config.propertyOrNull("ktor.deployment.shutdownTimeout")?.getString()?.toLong()
                        ?: 5000,
                    requestQueueLimit = config.propertyOrNull("ktor.deployment.requestQueueLimit")?.getString()?.toInt()
                        ?: 16,
                    runningLimit = config.propertyOrNull("ktor.deployment.runningLimit")?.getString()?.toInt() ?: 10,
                    responseWriteTimeoutSeconds = config
                        .propertyOrNull("ktor.deployment.responseWriteTimeoutSeconds")
                        ?.getString()
                        ?.toInt() ?: 10
                )
            )
        }
        get("log") {
            call.respond(InMemoryAppender.getSerializableRepresentation())
        }
        get("status") {
            call.respond(
                downloadManager.workerStatus()
            )
        }
        route("mediaLibrary") {
            mediaLibraryApi(mediaLibrary, duplicateCalculator, tagger, downloadManager, metadataStorage)
            mediaLibraryDebugApi(mediaLibrary)
        }

        downloaderApi(downloadManager, urlDecoder, mediaLibrary::addCompletedDownload)
        searcherApi()
        subtitleApi()

        route("autotags") {
            get("/popular") {
                val popular =
                    mediaLibrary
                        .getEntries()
                        .flatMap { it.withAutoTags(tagger).autoTags }
                        .groupingBy { it }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                call.respond(popular)
            }
        }
        userConfigReadWriteEndpoint("autotags")
        userConfigReadWriteEndpoint("queries")
    }
}


@TypeScript
@Serializable
data class MetadatedDownloadQueueEntry(val queueEntry: DownloadTaskDTO, val title: String)

@TypeScript
@Serializable
data class SearchRequest(val term: String, val offset: Int = 0)

@TypeScript
@Serializable
data class UrlRequest(val url: String)

@TypeScript
@Serializable
data class DuplicateResponse(val entry: MediaLibraryEntry, val possibleDuplicate: MediaLibraryEntry, val distance: Int)

@TypeScript
@Serializable
data class ApplicationConfig(
    val development: Boolean,
    val port: Int,
    val shutdownUrl: String,
    val connectionGroupSize: Int,
    val workerGroupSize: Int,
    val callGroupSize: Int,
    val shutdownGracePeriod: Long,
    val shutdownTimeout: Long,
    val requestQueueLimit: Int,
    val runningLimit: Int,
    val responseWriteTimeoutSeconds: Int,
)
