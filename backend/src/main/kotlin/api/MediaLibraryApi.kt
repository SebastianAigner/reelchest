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
import java.io.DataOutputStream
import java.io.File

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
                .entries
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
                mediaLibrary.entries.first { it.getDHashesFromDisk() == null && it !in hashingInProgress }
            hashingInProgress += entry
            call.respond(entry)
        }
        get("/all") {
            val res = mediaLibrary.entries.map {
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
            val restLibrary = mediaLibrary.entries
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