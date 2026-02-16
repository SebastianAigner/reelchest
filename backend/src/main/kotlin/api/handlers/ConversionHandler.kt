package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.conversion.*
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

    try {
        val task = conversionManager.enqueueConversion(
            sourceEntry = entry,
            mode = request.mode,
            options = request.options,
            convertAndDeleteOriginal = request.convertAndDeleteOriginal
        )
        call.respond(HttpStatusCode.Created, task)
    } catch (e: ConversionAlreadyQueuedException) {
        call.respond(HttpStatusCode.Conflict, e.message ?: "Conversion already queued.")
    }
}

suspend fun RoutingContext.enqueueDefaultConversionHandler(
    conversionManager: ConversionManager,
    metadataStorage: MetadataStorage,
) {
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest)
    val entry = metadataStorage.retrieveMetadata(id).just()
        ?: return call.respond(HttpStatusCode.NotFound)

    try {
        val task = conversionManager.enqueueConversion(
            sourceEntry = entry,
            mode = DEFAULT_CONVERSION_MODE,
            options = DEFAULT_CONVERSION_OPTIONS,
            convertAndDeleteOriginal = DEFAULT_CONVERT_AND_DELETE_ORIGINAL
        )
        call.respond(HttpStatusCode.Created, task)
    } catch (e: ConversionAlreadyQueuedException) {
        call.respond(HttpStatusCode.Conflict, e.message ?: "Conversion already queued.")
    }
}

suspend fun RoutingContext.enqueueDefaultMediaInfoConversionsHandler(
    conversionManager: ConversionManager,
    metadataStorage: MetadataStorage,
) {
    val sort = parseSort(call.request.queryParameters["sort"])
    val order = parseOrder(call.request.queryParameters["order"])
    val limit = parseLimit(call.request.queryParameters["limit"])
    val results = metadataStorage.searchMediaInformation(sort, order, limit)

    var enqueued = 0
    var skipped = 0
    var missing = 0

    results.forEach { summary ->
        val entry = metadataStorage.retrieveMetadata(summary.id).just()
        if (entry == null) {
            missing += 1
            return@forEach
        }
        val task = conversionManager.tryEnqueueConversion(
            sourceEntry = entry,
            mode = ConversionMode.SCHEDULED,
            options = DEFAULT_CONVERSION_OPTIONS,
            convertAndDeleteOriginal = DEFAULT_CONVERT_AND_DELETE_ORIGINAL
        )
        if (task == null) {
            skipped += 1
        } else {
            enqueued += 1
        }
    }

    call.respond(
        HttpStatusCode.Accepted,
        ConversionBatchSummary(
            total = results.size,
            enqueued = enqueued,
            skipped = skipped,
            missing = missing
        )
    )
}

suspend fun RoutingContext.clearConversionQueueHandler(
    conversionManager: ConversionManager,
) {
    val snapshot = conversionManager.clearQueue()
    call.respond(snapshot)
}
