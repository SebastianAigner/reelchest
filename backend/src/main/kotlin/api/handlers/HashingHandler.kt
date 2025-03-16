package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.*
import io.sebi.storage.MetadataStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.DataOutputStream
import java.io.File

val hashingInProgress = mutableListOf<MediaLibraryEntry>()

/**
 * Handler for getting unhashed entries.
 */
@OptIn(ExperimentalUnsignedTypes::class)
suspend fun RoutingContext.getUnhashedEntryHandler(
    mediaLibrary: MediaLibrary,
    hashingInProgress: MutableList<MediaLibraryEntry>,
) {
    val entry =
        mediaLibrary.getEntries().first { it.getDHashesFromDisk() == null && it !in hashingInProgress }
    hashingInProgress += entry
    call.respond(entry)
}

/**
 * Handler for getting all hashes.
 */
@OptIn(ExperimentalUnsignedTypes::class)
suspend fun RoutingContext.getAllHashesHandler(mediaLibrary: MediaLibrary) {
    val res = mediaLibrary.getEntries().map {
        yield()
        buildJsonObject {
            put("id", it.id)
            put("hashes", Json.encodeToJsonElement(it.getDHashesFromDisk()))
        }
    }
    call.respond(res)
}

/**
 * Handler for adding a hash for an entry.
 */
suspend fun RoutingContext.saveHashHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    val ba = call.receive<ByteArray>()
    val individual = metadataStorage.retrieveMetadata(id).just()
        ?: return call.respond("Not found")
    File(individual.file!!.parentFile, "dhashes.bin").writeBytes(ba)
    call.respond("OK")
}

/**
 * Handler for getting a hash by format.
 */
@OptIn(ExperimentalUnsignedTypes::class)
suspend fun RoutingContext.getHashByFormatHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    val format = call.parameters["format"]!!
    val dhashes = metadataStorage.retrieveMetadata(id).just()?.getDHashesFromDisk() ?: return call.respond(
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

        else -> call.respond(HttpStatusCode.BadRequest)
    }
}
