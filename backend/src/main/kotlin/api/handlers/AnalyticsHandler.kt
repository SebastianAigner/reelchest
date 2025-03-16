package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.analytics.AnalyticsDatabase

/**
 * Handler for logging analytics events.
 */
suspend fun RoutingContext.logEventHandler(analyticsDatabase: AnalyticsDatabase) {
    val eventText = call.receiveText()
    analyticsDatabase.log(eventText)
    call.respond(HttpStatusCode.OK)
}
