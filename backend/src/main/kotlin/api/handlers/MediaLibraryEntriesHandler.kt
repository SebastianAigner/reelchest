package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.dtos.UrlRequest
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.DownloadManager
import io.sebi.ffmpeg.getMediaProbeInfo
import io.sebi.ffmpeg.toMimeType
import io.sebi.library.*
import io.sebi.storage.*
import io.sebi.tagging.Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun parseSort(sortParam: String?): MediaInfoSort {
    return when (sortParam?.lowercase()) {
        "resolution", "res" -> MediaInfoSort.RESOLUTION
        "framerate", "frame-rate", "fps", "frame" -> MediaInfoSort.FRAME_RATE
        "filesize", "file-size", "size" -> MediaInfoSort.FILE_SIZE
        "bitrate", "bit-rate", "br" -> MediaInfoSort.BIT_RATE
        "duration", "durationseconds", "length" -> MediaInfoSort.DURATION
        "width", "w" -> MediaInfoSort.WIDTH
        "height", "h" -> MediaInfoSort.HEIGHT
        else -> MediaInfoSort.RESOLUTION
    }
}

private fun parseOrder(orderParam: String?): SortOrder {
    return when (orderParam?.lowercase()) {
        "asc", "ascending" -> SortOrder.ASC
        "desc", "descending" -> SortOrder.DESC
        else -> SortOrder.DESC
    }
}

private fun parseLimit(limitParam: String?): Int {
    val parsed = limitParam?.toIntOrNull()
    return parsed?.coerceIn(1, 100) ?: 10
}

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
 * Handler for querying media information across the library.
 */
suspend fun RoutingContext.searchMediaInformationHandler(metadataStorage: MetadataStorage) {
    val sort = parseSort(call.request.queryParameters["sort"])
    val order = parseOrder(call.request.queryParameters["order"])
    val limit = parseLimit(call.request.queryParameters["limit"])
    val results = metadataStorage.searchMediaInformation(sort, order, limit)
    call.respond(results)
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
    call.application.launch {
        metadataStorage.retrieveMetadata(id).just()?.addHitAndPersist(metadataStorage)
    }
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
        val mediaFile = entry.file
        if (!mediaFile.exists()) {
            return@withContext call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "File not found")
            })
        }

        val cached = metadataStorage.getMediaInformation(entry.id)
        if (cached != null) {
            if (cached.probeStatus == MediaProbeStatus.FAILED) {
                val errorMessage = cached.probeError ?: "Unknown error occurred while determining media information"
                if (errorMessage == "File not found") {
                    return@withContext call.respond(HttpStatusCode.NotFound, buildJsonObject {
                        put("error", "File not found")
                    })
                }
                return@withContext call.respond(HttpStatusCode.InternalServerError, buildJsonObject {
                    put("error", errorMessage)
                })
            }
            return@withContext call.respond(buildJsonObject {
                put("mimeType", cached.mimeType)
                put("width", cached.width)
                put("height", cached.height)
                put("bitRate", cached.bitRate)
                put("frameRate", cached.frameRate)
                put("fileSize", cached.fileSize)
                put("durationSeconds", cached.durationSeconds)
            })
        }

        try {
            val probeInfo = getMediaProbeInfo(mediaFile)
            val mediaType = probeInfo.mediaTypeInfo
            val mimeType = mediaType.toMimeType()
            val persisted = MediaInformation(
                libraryEntryId = entry.id,
                mimeType = mimeType,
                codecType = mediaType.codecType,
                codecName = mediaType.codecName,
                width = mediaType.width,
                height = mediaType.height,
                bitRate = mediaType.bitRate,
                frameRate = mediaType.frameRate,
                fileSize = mediaType.fileSize,
                durationSeconds = probeInfo.durationSeconds,
                probeStatus = MediaProbeStatus.SUCCESS,
                probeError = null,
                lastProbeAt = System.currentTimeMillis(),
            )
            metadataStorage.upsertMediaInformation(persisted)
            call.respond(buildJsonObject {
                put("mimeType", persisted.mimeType)
                put("width", persisted.width)
                put("height", persisted.height)
                put("bitRate", persisted.bitRate)
                put("frameRate", persisted.frameRate)
                put("fileSize", persisted.fileSize)
                put("durationSeconds", persisted.durationSeconds)
            })
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error occurred while determining media information"
            metadataStorage.upsertMediaInformation(
                MediaInformation(
                    libraryEntryId = entry.id,
                    mimeType = null,
                    codecType = null,
                    codecName = null,
                    width = null,
                    height = null,
                    bitRate = null,
                    frameRate = null,
                    fileSize = null,
                    durationSeconds = null,
                    probeStatus = MediaProbeStatus.FAILED,
                    probeError = errorMessage,
                    lastProbeAt = System.currentTimeMillis(),
                )
            )
            call.respond(HttpStatusCode.InternalServerError, buildJsonObject {
                put("error", errorMessage)
            })
        }
    }
}

/**
 * Handler for updating a media library entry.
 */
suspend fun RoutingContext.updateMediaLibraryEntryHandler(metadataStorage: MetadataStorage) {
    val newEntry = call.receive<MediaLibraryEntry>()
    call.application.launch {
        newEntry.persist(metadataStorage)
    }
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
