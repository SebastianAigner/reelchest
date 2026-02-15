package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.conversion.ConversionManager
import io.sebi.conversion.ConversionRequest
import io.sebi.storage.MetadataStorage

suspend fun RoutingContext.getConversionQueueHandler(conversionManager: ConversionManager) {
    call.respond(conversionManager.snapshot())
}

suspend fun RoutingContext.getConversionSummaryHandler(
    conversionManager: ConversionManager,
) {
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest)
    call.respond(conversionManager.summaryForSource(id))
}

suspend fun RoutingContext.enqueueConversionHandler(
    conversionManager: ConversionManager,
    metadataStorage: MetadataStorage,
) {
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest)
    val request = call.receive<ConversionRequest>()
    val entry = metadataStorage.retrieveMetadata(id).just()
        ?: return call.respond(HttpStatusCode.NotFound)

    try {
        conversionManager.validateOptions(request.options)
    } catch (e: Exception) {
        return call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid conversion options.")
    }

    val task = conversionManager.enqueueConversion(entry, request.mode, request.options)
    call.respond(HttpStatusCode.Created, task)
}
