package io.sebi.api.handlers

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.library.MediaLibrary
import io.sebi.library.withAutoTags
import io.sebi.tagging.Tagger

/**
 * Handler for the popular autotags endpoint.
 */
suspend fun RoutingContext.popularAutotagsHandler(mediaLibrary: MediaLibrary, tagger: Tagger) {
    val popular =
        mediaLibrary
            .getEntries()
            .flatMap { it.withAutoTags(tagger).autoTags }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    call.respond(popular)
}
