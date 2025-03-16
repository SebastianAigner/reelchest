package io.sebi.api

import io.ktor.server.routing.*
import io.sebi.analytics.AnalyticsDatabase
import io.sebi.api.handlers.*
import io.sebi.downloader.DownloadManager
import io.sebi.library.MediaLibrary
import io.sebi.search.RPCSearchServiceProvider
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import io.sebi.urldecoder.UrlDecoder
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Api")


private val analyticsDatabase = AnalyticsDatabase()

@OptIn(ExperimentalStdlibApi::class)
fun Route.api(
    urlDecoder: UrlDecoder,
    mediaLibrary: MediaLibrary,
    downloadManager: DownloadManager,
    tagger: Tagger,
    metadataStorage: MetadataStorage,
) {

    route("api") {
        route("analytics") {
            post("event") { logEventHandler(analyticsDatabase) }
        }
        get("config", RoutingContext::configHandler)
        get("log", RoutingContext::logHandler)
        get("status") { statusHandler(downloadManager) }
        route("mediaLibrary") {
            get { getMediaLibraryEntriesHandler(mediaLibrary, tagger) }
            post("isUrlInLibraryOrProgress") { isUrlInLibraryOrProgressHandler(downloadManager, mediaLibrary) }

            route("duplicates") {
                get("{id}") { getDuplicateByIdHandler(metadataStorage) }
                post("{id}") { createDuplicateHandler(metadataStorage) }
                get { listAllDuplicatesHandler() }
            }

            route("hashing") {
                get("unhashed") { getUnhashedEntryHandler(mediaLibrary, hashingInProgress) }
                get("/all") { getAllHashesHandler(mediaLibrary) }
                post("/hash/{id}") { saveHashHandler(metadataStorage) }
            }

            route("{id}") {
                get { getMediaLibraryEntryByIdHandler(metadataStorage, tagger) }
                post { updateMediaLibraryEntryHandler(metadataStorage) }

                route("storedDuplicate") {
                    get { retrieveStoredDuplicateHandler(metadataStorage) }
                    post { createStoredDuplicateHandler(metadataStorage) }
                }

                get("hash.{format}") { getHashByFormatHandler(metadataStorage) }
                get("hit") { recordHitHandler(metadataStorage) }
                get("media-information") { getMediaInformationHandler(metadataStorage) }
                get("thumbnails") { getThumbnailsHandler(mediaLibrary) }
                get("possibleDuplicates") { findPossibleDuplicatesHandler() }
                get("randomThumb") { getRandomThumbHandler(mediaLibrary) }
            }

            route("debug") {
                get("missing-thumbnails") { getMissingThumbnailsHandler(mediaLibrary) }
                get("regenerate-thumbnail/{id}") { regenerateThumbnailHandler(mediaLibrary) }
            }
        }

        route("download") {
            post { downloadUrlHandler(downloadManager, urlDecoder, mediaLibrary::addCompletedDownload) }
        }
        route("queue") {
            get { getDownloadQueueHandler(downloadManager) }
        }
        route("problematic") {
            get { getProblematicDownloadsHandler(downloadManager) }
            post("remove") { removeProblematicDownloadHandler(downloadManager) }
            post("retry") {
                retryProblematicDownloadHandler(
                    downloadManager,
                    urlDecoder,
                    mediaLibrary::addCompletedDownload
                )
            }
        }
        val searchServiceProvider = RPCSearchServiceProvider()
        route("search") {
            post("{provider}") { searchHandler(searchServiceProvider) }
        }
        route("searchers") {
            get { getSearchersHandler(searchServiceProvider) }
        }
        route("subtitles") {
            post("shiftsubtitles") { shiftSubtitlesHandler() }
            post("embedsubtitles") { embedSubtitlesHandler() }
        }

        route("autotags") {
            get("/popular") { popularAutotagsHandler(mediaLibrary, tagger) }
        }
        route("autotags") {
            get { userConfigReadHandler("autotags") }
            post { userConfigWriteHandler("autotags") }
        }
        route("queries") {
            get { userConfigReadHandler("queries") }
            post { userConfigWriteHandler("queries") }
        }

        route("file") {
            get("{filename}") { serveFileByFilenameHandler(mediaLibrary) }
        }
        route("video") {
            get("{id}") { serveVideoByIdHandler(mediaLibrary) }
        }
    }
    get("/decrypt") {
        decryptHandler(urlDecoder)
    }
    post("/ul") {
        uploadHandler(mediaLibrary)
    }
}
