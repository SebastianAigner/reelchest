package io.sebi.api

import dz.jtsgen.annotations.TypeScript
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.InMemoryAppender
import io.sebi.analytics.analyticsApi
import io.sebi.downloader.DownloadManager
import io.sebi.downloader.DownloadTaskDTO
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.network.NetworkManager
import io.sebi.storage.MetadataStorage
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
        get("log") {
            call.respond(InMemoryAppender.getSerializableRepresentation())
        }
        route("mediaLibrary") {
            mediaLibraryApi(mediaLibrary, duplicateCalculator, tagger, downloadManager, metadataStorage)
        }

        downloaderApi(downloadManager, urlDecoder, mediaLibrary::addCompletedDownload)
        searcherApi()

        route("autotags") {
            get("/popular") {
                val popular =
                    mediaLibrary
                        .entries
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