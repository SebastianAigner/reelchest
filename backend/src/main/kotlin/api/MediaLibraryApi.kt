package io.sebi.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.datastructures.shaHashed
import io.sebi.downloader.DownloadManager
import io.sebi.duplicatecalculator.DuplicateCalculator
import io.sebi.library.MediaLibrary
import io.sebi.library.MediaLibraryEntry
import io.sebi.phash.getMinimalDistance
import io.sebi.storage.MetadataStorage
import io.sebi.tagging.Tagger
import java.io.File

@OptIn(ExperimentalStdlibApi::class)
fun Route.mediaLibraryApi(
    mediaLibrary: MediaLibrary,
    duplicateCalculator: DuplicateCalculator,
    tagger: Tagger,
    downloadManager: DownloadManager,
    metadataStorage: MetadataStorage,
) {
    get {
        val mediaLib = mediaLibrary.entries.map { it.withoutPage() }.sortedByDescending {
            it.creationDate
        }.distinctBy { it.id }
        if (this.context.request.queryParameters["auto"] != null) {
            return@get call.respond(mediaLib.map { it.withAutoTags(tagger) })
        }
        call.respond(mediaLib)
    }
    route("duplicates") {
        get {
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
                mediaLibrary.entries.first { it.getDHashes() == null && it !in hashingInProgress }
            hashingInProgress += entry
            call.respond(entry)
        }
        post("/hash/{id}") {
            val id = call.parameters["id"]!!
            val ba = call.receive<ByteArray>()
            val individual = metadataStorage.retrieveMetadata(id).just()?.withoutPage()
                ?: return@post call.respond("Not found")
            File(individual.file!!.parentFile, "dhashes.bin").writeBytes(ba)
            call.respond("OK")
        }
    }
    route("{id}") {
        get {
            val id = call.parameters["id"]!!
            val auto = call.parameters["auto"] != null
            val individual = metadataStorage.retrieveMetadata(id).just()?.withoutPage() ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
            call.respond(if (auto) individual.withAutoTags(tagger) else individual)
        }
        get("hit") {
            val id = call.parameters["id"]!!
            metadataStorage.retrieveMetadata(id).just()?.addHitAndPersist(metadataStorage)
            call.respond(HttpStatusCode.OK)
        }
        post {
            val newEntry = call.receive<MediaLibraryEntry>()
            val existingEntry = mediaLibrary.findById(newEntry.id)!!
            newEntry.originPage = existingEntry.originPage // todo: refactor
            newEntry.persist(metadataStorage)
            call.respond(HttpStatusCode.OK)
        }
    }

    get("{id}/thumbnails") {
        val id = call.parameters["id"]!!
        val entry = mediaLibrary.findById(id)!!
        val out = entry.file!!.parentFile.list()!!.filter { it.startsWith("thumb") }.sorted()
        call.respond(out)
    }

    get("{id}/possibleDuplicates") {
        val id = call.parameters["id"]!!
        val entry = mediaLibrary.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        val restLibrary = mediaLibrary.entries
            .filterNot { it.id == id }
            .mapNotNull { curr ->
                val dhash = curr.getDHashes() ?: return@mapNotNull null
                curr to dhash
            }
        // we randomly pick a handful of hashes from our candidate.
        val entryHashes = entry.getDHashes()
        val handful = entryHashes?.shuffled()?.take(100).orEmpty()
        // we find the global minimum: which of the other library entries has the lowest cumulative distance?
        val mostLikelyDuplicate = restLibrary.minByOrNull { (_, dhashes) ->
            handful.sumOf { dhashes.getMinimalDistance(it) }
        }
        if (mostLikelyDuplicate != null) call.respond(mostLikelyDuplicate.first)
        else call.respond(HttpStatusCode.NotFound)
    }
    get("{id}/randomThumb") {
        val id = call.parameters["id"]!!
        val entry =
            mediaLibrary
                .findById(id)
                ?.getThumbnails()
                ?.randomOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondFile(entry)
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