package io.sebi.api

import dz.jtsgen.annotations.TypeScript
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.DownloadManager
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.ffmpeg.MediaTypeInfo
import io.sebi.ffmpeg.generateThumbnails
import io.sebi.ffmpeg.getMediaType
import io.sebi.library.*
import io.sebi.phash.DHash
import io.sebi.phash.getMinimalDistance
import io.sebi.sqldelight.mediametadata.Duplicates
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@TypeScript
@Serializable
data class DuplicatesDTO(
    val src_id: String,
    val dup_id: String,
    val distance: Long,
)

fun DuplicatesDTO.Companion.from(d: Duplicates): DuplicatesDTO {
    return DuplicatesDTO(d.src_id, d.dup_id, d.distance)
}

val mimeTypeCache = ConcurrentHashMap<String, String>()
val mediaTypeCache = ConcurrentHashMap<String, MediaTypeInfo>()

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun Route.mediaLibraryApi(
    mediaLibrary: MediaLibrary,
    duplicateCalculator: DuplicateCalculator,
    tagger: Tagger,
    downloadManager: DownloadManager,
    metadataStorage: MetadataStorage,
) {
    get {
        val mediaLib =
            mediaLibrary
                .getEntries()
                .sortedByDescending {
                    it.creationDate
                }
                .distinctBy { it.id }
        if (this.context.request.queryParameters["auto"] != null) {
            return@get call.respond(mediaLib.map { it.withAutoTags(tagger) })
        }
        call.respond(mediaLib)
    }
    route("duplicates") {
        get("{id}") {
            val id = call.parameters["id"]!!
            val x = metadataStorage.getDuplicate(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(DuplicatesDTO.from(x))
        }
        post("{id}") {
            val id = call.parameters["id"]!!
            val ddto = call.receive<DuplicatesDTO>()
            metadataStorage.addDuplicate(ddto.src_id, ddto.dup_id, ddto.distance.toInt())
            call.respond(HttpStatusCode.OK)
        }
        get {
            return@get call.respond(emptyList<DuplicateResponse>())
            val dups = duplicateCalculator.duplicatesMap ?: error("No duplicates map.")
            val lst = dups
                .toList()
                .distinctBy { setOf(it.first, it.second.entry) }
                .sortedBy { (k, v) -> v.distance }
                .map { (entry, dup) ->
                    DuplicateResponse(entry, dup.entry, dup.distance)
                }
            call.respond(lst)
        }
    }
    route("hashing") {
        get("unhashed") {
            val entry =
                mediaLibrary.getEntries().first { it.getDHashesFromDisk() == null && it !in hashingInProgress }
            hashingInProgress += entry
            call.respond(entry)
        }
        get("/all") {
            val res = mediaLibrary.getEntries().map {
                yield()
                buildJsonObject {
                    put("id", it.id)
                    put("hashes", Json.encodeToJsonElement(it.getDHashesFromDisk()))
                }
            }
            call.respond(res)
        }
        post("/hash/{id}") {
            val id = call.parameters["id"]!!
            val ba = call.receive<ByteArray>()
            val individual = metadataStorage.retrieveMetadata(id).just()
                ?: return@post call.respond("Not found")
            File(individual.file!!.parentFile, "dhashes.bin").writeBytes(ba)
            call.respond("OK")
        }
    }
    route("{id}") {
        get {
            val id = call.parameters["id"]!!
            val auto = call.parameters["auto"] != null
            val individual = metadataStorage.retrieveMetadata(id).just() ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
            call.respond(if (auto) individual.withAutoTags(tagger) else individual)
        }
        get("hash.{format}") {
            val id = call.parameters["id"]!!
            val format = call.parameters["format"]!!
            val dhashes = metadataStorage.retrieveMetadata(id).just()?.getDHashesFromDisk() ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
            when (format) {
                "json" -> {
                    call.respond(dhashes)
                }

                "bin" -> {
                    // create a new dataoutputstream to put the ULongs
                    call.respondOutputStream {
                        withContext(Dispatchers.IO) {
                            val dos = DataOutputStream(this@respondOutputStream)
                            for (hash in dhashes) {
                                dos.writeLong(hash.toLong())
                            }
                        }
                    }
                }
            }
            call.respond(HttpStatusCode.BadRequest)
        }
        get("hit") {
            val id = call.parameters["id"]!!
            metadataStorage.retrieveMetadata(id).just()?.addHitAndPersist(metadataStorage)
            call.respond(HttpStatusCode.OK)
        }
        get("media-information") {
            withContext(Dispatchers.IO) {
                val id = call.parameters["id"]!!
                val entry =
                    metadataStorage.retrieveMetadata(id).just()
                        ?: return@withContext call.respond(HttpStatusCode.NotFound)
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
        get("thumbnails") {
            val id = call.parameters["id"]!!
            val entry = mediaLibrary.findById(id)!!
            val out = entry.file!!.parentFile.list()!!.filter { it.startsWith("thumb") }.sorted()
            call.respond(out)
        }
        get("possibleDuplicates") {
            return@get call.respond(
                HttpStatusCode.NotFound,
                "Duplicate detection is disabled because low-end devices can't handle the memory issues"
            )
            val id = call.parameters["id"]!!
            val entry = mediaLibrary.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            val restLibrary = mediaLibrary.getEntries()
                .filterNot { it.id == id }
                .mapNotNull { curr ->
                    val dhash = curr.getDHashesFromDisk() ?: return@mapNotNull null
                    yield()
                    curr to dhash
                }
            // we randomly pick a handful of hashes from our candidate.
            val entryHashes = entry.getDHashesFromDisk()
            val handful = entryHashes?.shuffled()?.take(100).orEmpty()
            // we find the global minimum: which of the other library entries has the lowest cumulative distance?
            val mostLikelyDuplicate = restLibrary.minByOrNull { (_, dhashes) ->
                yield()
                handful.sumOf { getMinimalDistance(dhashes, DHash(it)) }
            }
            if (mostLikelyDuplicate != null) call.respond(mostLikelyDuplicate.first)
            else call.respond(HttpStatusCode.NotFound)
        }
        get("randomThumb") {
            val id = call.parameters["id"]!!
            val entry =
                mediaLibrary
                    .findById(id)
                    ?.getThumbnails()
                    ?.randomOrNull()
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondFile(entry)
        }
        post {
            val newEntry = call.receive<MediaLibraryEntry>()
            newEntry.persist(metadataStorage)
            call.respond(HttpStatusCode.OK)
        }
    }
    post("isUrlInLibraryOrProgress") {
        val url = call.receive<UrlRequest>().url
        val sha = url.shaHashed()

        val isInLibOrProgress =
            downloadManager.allDownloads.any { it.originUrl == url } ||
                    mediaLibrary.existsOrTombstone(sha)
        call.respond(isInLibOrProgress)
    }
}

@Serializable
data class ThumbnailDebugResponse(
    val status: String,
    val debug_output: List<String>,
)

fun Route.mediaLibraryDebugApi(mediaLibrary: MediaLibrary) {
    route("debug") {
        get("missing-thumbnails") {
            val entriesWithoutThumbnails = mediaLibrary.getEntries().filter { entry ->
                val thumbnails = entry.getThumbnails()
                thumbnails == null || thumbnails.isEmpty()
            }
            call.respond(entriesWithoutThumbnails)
        }

        get("regenerate-thumbnail/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id parameter")
            val entry = mediaLibrary.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Entry not found")

            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.header("Access-Control-Allow-Origin", "http://localhost:8080")
            call.response.header("Access-Control-Allow-Credentials", "true")
            call.response.header("Cache-Control", "no-cache")
            call.response.header("Connection", "keep-alive")
            try {
                entry.file?.let { videoFile ->
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        val logger = object : org.slf4j.Logger by LoggerFactory.getLogger("ThumbnailDebug") {
                            override fun info(msg: String) {
                                write("data: [INFO] $msg\n\n")
                                flush()
                            }

                            override fun error(msg: String) {
                                write("data: [ERROR] $msg\n\n")
                                flush()
                            }
                        }

                        write("data: Starting thumbnail regeneration for entry: ${entry.name}\n\n")
                        write("data: Video file: ${videoFile.absolutePath}\n\n")
                        flush()

                        generateThumbnails(
                            videoFile,
                            logger,
                            onStdoutLine = { line ->
                                write("data: [FFMPEG-OUT] $line\n\n")
                                flush()
                            },
                            onStderrLine = { line ->
                                write("data: [FFMPEG-ERR] $line\n\n")
                                flush()
                            }
                        )

                        write("data: Thumbnail generation completed\n\n")
                        flush()
                    }
                } ?: call.respond(HttpStatusCode.NotFound, "Video file not found")
            } catch (e: Exception) {
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    write("data: [ERROR] Exception: ${e.message}\n\n")
                    flush()
                }
            }
        }
    }
}
