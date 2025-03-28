package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.api.dtos.DuplicatesDTO
import io.sebi.storage.Duplicates
import io.sebi.storage.MetadataStorage

/**
 * Extension function to convert a Duplicates object to a DuplicatesDTO.
 */
fun DuplicatesDTO.Companion.from(d: Duplicates): DuplicatesDTO {
    return DuplicatesDTO(d.src_id, d.dup_id, d.distance)
}

/**
 * Handler for getting duplicates by ID.
 */
suspend fun RoutingContext.getDuplicateByIdHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    val x = metadataStorage.getDuplicate(id) ?: return call.respond(HttpStatusCode.NotFound)
    call.respond(DuplicatesDTO.from(x))
}

/**
 * Handler for adding a duplicate.
 */
suspend fun RoutingContext.createDuplicateHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    val ddto = call.receive<DuplicatesDTO>()
    metadataStorage.addDuplicate(ddto.src_id, ddto.dup_id, ddto.distance.toInt())
    call.respond(HttpStatusCode.OK)
}

/**
 * Handler for getting all duplicates.
 */
suspend fun RoutingContext.listAllDuplicatesHandler(metadataStorage: MetadataStorage) {
    val topDuplicates = metadataStorage.getTopDuplicates(Integer.MAX_VALUE)
    val duplicatesDTO = topDuplicates.map { DuplicatesDTO.from(it) }
    call.respond(duplicatesDTO)
}

/**
 * Handler for getting a stored duplicate.
 */
suspend fun RoutingContext.retrieveStoredDuplicateHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    // Check if the media library entry exists
    val individual = metadataStorage.retrieveMetadata(id).just() ?: return call.respond(
        HttpStatusCode.NotFound
    )
    val duplicate = metadataStorage.getDuplicate(id) ?: return call.respond(
        HttpStatusCode.NotFound
    )
    call.respond(DuplicatesDTO.from(duplicate))
}

/**
 * Handler for adding a stored duplicate.
 */
suspend fun RoutingContext.createStoredDuplicateHandler(metadataStorage: MetadataStorage) {
    val id = call.parameters["id"]!!
    // Check if the media library entry exists
    val individual = metadataStorage.retrieveMetadata(id).just() ?: return call.respond(
        HttpStatusCode.NotFound
    )
    val ddto = call.receive<DuplicatesDTO>()
    metadataStorage.addDuplicate(id, ddto.dup_id, ddto.distance.toInt())
    call.respond(HttpStatusCode.OK)
}

/**
 * Handler for getting possible duplicates.
 */
suspend fun RoutingContext.findPossibleDuplicatesHandler() {
    call.respond(
        HttpStatusCode.NotFound,
        "Duplicate detection is disabled because low-end devices can't handle the memory issues"
    )
}
