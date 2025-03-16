package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.dtos.UrlRequest
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.DownloadManager
import io.sebi.ffmpeg.MediaTypeInfo
import io.sebi.ffmpeg.getMediaType
import io.sebi.library.*
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

// Cache variables from original file
private val mimeTypeCache = ConcurrentHashMap<String, String>()
private val mediaTypeCache = ConcurrentHashMap<String, MediaTypeInfo>()

/**
 * Handler for getting all media library entries.
 */
suspend fun RoutingContext.getMediaLibraryEntriesHandler(mediaLibrary: MediaLibrary, tagger: Tagger) {
    val mediaLib =
        mediaLibrary
            .getEntries()
            .sortedByDescending {
                it.creationDate
            }
            .distinctBy { it.id }
    if (call.request.queryParameters["auto"] != null) {
        return call.respond(mediaLib.map { it.withAutoTags(tagger) })
    }
    call.respond(mediaLib)
}

/**
 * Handler for getting a media library entry by ID.
 */
suspend fun RoutingContext.getMediaLibraryEntryByIdHandler(metadataStorage: MetadataStorage, tagger: Tagger) {
    val id = call.parameters["id"]!!
    val auto = call.parameters["auto"] != null
    val individual = metadataStorage.retrieveMetadata(id).just() ?: return call.respond(
        HttpStatusCode.NotFound
    )
    call.respond(if (auto) individual.withAutoTags(tagger) else individual)
}

/**
 * Handler for recording a hit on a media library entry.
 */
suspend fun RoutingContext.recordHitHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    metadataStorage.retrieveMetadata(id).just()?.addHitAndPersist(metadataStorage)
    call.respond(HttpStatusCode.OK)
}

/**
 * Handler for getting media information.
 */
suspend fun RoutingContext.getMediaInformationHandler(metadataStorage: MetadataStorage) {
    withContext(Dispatchers.IO) {
        val id = call.parameters["id"]!!
        val entry =
            metadataStorage.retrieveMetadata(id).just()
                ?: return@withContext call.respond(HttpStatusCode.NotFound)
        if (entry.file == null || !entry.file.exists()) {
            return@withContext call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "File not found")
            })
        }

        try {
            val mimeType = mimeTypeCache.getOrPut(entry.id) { entry.getMimeType() }
            val mediaInfo = mediaTypeCache.getOrPut(entry.id) { getMediaType(entry.file) }
            call.respond(buildJsonObject {
                put("mimeType", mimeType)
                put("width", mediaInfo.width)
                put("height", mediaInfo.height)
            })
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, buildJsonObject {
                put("error", e.message ?: "Unknown error occurred while determining media information")
            })
        }
    }
}

/**
 * Handler for updating a media library entry.
 */
suspend fun RoutingContext.updateMediaLibraryEntryHandler(metadataStorage: MetadataStorage) {
    val newEntry = call.receive<MediaLibraryEntry>()
    newEntry.persist(metadataStorage)
    call.respond(HttpStatusCode.OK)
}

/**
 * Handler for checking if a URL is in the library or in progress.
 */
suspend fun RoutingContext.isUrlInLibraryOrProgressHandler(
    downloadManager: DownloadManager,
    mediaLibrary: MediaLibrary,
) {
    val url = call.receive<UrlRequest>().url
    val sha = url.shaHashed()

    val isInLibOrProgress =
        downloadManager.allDownloads.any { it.originUrl == url } ||
                mediaLibrary.existsOrTombstone(sha)
    call.respond(isInLibOrProgress)
}
