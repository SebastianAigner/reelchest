package io.sebi.api.handlers

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sebi.analytics.AnalyticsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handler for logging analytics events.
 */
suspend fun RoutingContext.logEventHandler(analyticsDatabase: AnalyticsDatabase) {
    val eventText = call.receiveText()
    call.application.launch(Dispatchers.IO) {
        analyticsDatabase.log(eventText)
    }
    call.respond(HttpStatusCode.OK)
}
